#!/usr/bin/env bash
# =====================================================================
# Prueba de humo end-to-end de la EFT (ejecutar con la plataforma levantada).
#   HOST=localhost ./scripts/smoke-test.sh        (en la EC2: HOST=<IP publica>)
# Recorre: OAuth2 -> clientes -> cuentas -> pagos/transferencia (saga) -> Kafka/alertas
#          -> jobs Spring Batch -> BFF cajero/web/movil. Requiere curl y python3.
# =====================================================================
set -u
HOST="${HOST:-localhost}"
GW="http://$HOST:8080"
AUTH="http://$HOST:9000"
ok=0; fail=0

json() { python3 -c "import sys,json; d=json.load(sys.stdin); print($1)" 2>/dev/null; }
token() { curl -s -u "$1:$2" -d "grant_type=client_credentials" --data-urlencode "scope=$3" "$AUTH/oauth2/token" | json "d['access_token']"; }
check() { # descripcion esperado obtenido
  if [ "$2" = "$3" ]; then echo "  OK   $1 ($3)"; ok=$((ok+1)); else echo "  FAIL $1 (esperado $2, obtenido $3)"; fail=$((fail+1)); fi
}
code() { curl -s -o /dev/null -w "%{http_code}" "$@"; }

echo "== 1. Seguridad OAuth2"
ADMIN=$(token admin-client admin123 "clientes.read clientes.write cuentas.read cuentas.write pagos.read pagos.write batch.run")
[ -n "$ADMIN" ] && echo "  token admin obtenido" || { echo "  no se pudo obtener token: auth-server arriba?"; exit 1; }
H="Authorization: Bearer $ADMIN"
check "sin token => 401" 401 "$(code $GW/clientes)"
CAJERO=$(token cajero-client cajero123 "cajero.read cajero.write")
check "token de cajero en ms-clientes => 403" 403 "$(code -H "Authorization: Bearer $CAJERO" $GW/clientes)"

echo "== 2. Clientes y cuentas"
RUT="$((10000000 + RANDOM * 7 % 89999999))-K"
CLI=$(curl -s -H "$H" -H "Content-Type: application/json" -d "{\"rut\":\"$RUT\",\"nombre\":\"Ana Prueba\",\"email\":\"ana@banco.cl\",\"perfil\":\"PREMIUM\"}" $GW/clientes | json "d['id']")
echo "  cliente id=$CLI"
C1=$(curl -s -H "$H" -H "Content-Type: application/json" -d "{\"clienteId\":$CLI,\"tipo\":\"CORRIENTE\",\"alias\":\"Origen\",\"saldoInicial\":2000000}" $GW/cuentas | json "d['id']")
C2=$(curl -s -H "$H" -H "Content-Type: application/json" -d "{\"clienteId\":$CLI,\"tipo\":\"AHORRO\",\"alias\":\"Destino\"}" $GW/cuentas | json "d['id']")
echo "  cuentas $C1 (origen) y $C2 (destino)"
check "cuenta para cliente inexistente => 422" 422 "$(code -H "$H" -H 'Content-Type: application/json' -d '{"clienteId":999999,"tipo":"AHORRO"}' $GW/cuentas)"

echo "== 3. Pagos (saga + idempotencia)"
check "transferencia 500000 => 200" 200 "$(code -H "$H" -H 'Content-Type: application/json' -H 'Idempotency-Key: smoke-'$RANDOM'-t1' -d "{\"cuentaOrigenId\":$C1,\"cuentaDestinoId\":$C2,\"monto\":500000}" $GW/pagos/transferencia)"
KEY="smoke-$RANDOM-t2"
check "transferencia a cuenta inexistente => 404 (revertida)" 404 "$(code -H "$H" -H 'Content-Type: application/json' -H "Idempotency-Key: $KEY" -d "{\"cuentaOrigenId\":$C1,\"cuentaDestinoId\":999999,\"monto\":1000}" $GW/pagos/transferencia)"
echo "  estado de esa saga: $(curl -s -H "$H" $GW/pagos/$KEY | json "d['estado']")  (esperado COMPENSADO)"
echo "  saldo origen (esperado 1500000): $(curl -s -H "$H" $GW/cuentas/$C1 | json "d['saldo']")"
check "saldo insuficiente => 422" 422 "$(code -H "$H" -H 'Content-Type: application/json' -d "{\"cuentaId\":$C2,\"monto\":99999999}" $GW/pagos/pago)"
echo "  deposito grande (dispara alerta de seguridad por Kafka)"
code -H "$H" -H 'Content-Type: application/json' -d "{\"cuentaId\":$C2,\"monto\":1500000}" $GW/pagos/deposito >/dev/null
sleep 4
echo "  alertas del cliente: $(curl -s -H "$H" $GW/clientes/$CLI/alertas | json "len(d)") (esperado >= 1)"
echo "  actividad de la cuenta destino: $(curl -s -H "$H" $GW/cuentas/$C2/actividad | json "d['totalTransacciones']") transacciones"

echo "== 4. Spring Batch"
for j in transacciones intereses estados; do
  echo "  lanzando $j: $(curl -s -X POST -H "$H" $GW/batch/jobs/$j | json "d['estado']")"
done
sleep 20
for j in transacciones intereses estados; do
  echo "  $j => $(curl -s -H "$H" $GW/batch/jobs/$j/ultima | json "d['estado']")"
done
echo "  conciliacion: $(curl -s -H "$H" "$GW/batch/reportes/conciliacion?limite=3" | json "[(r['job_name'],r['consistente']) for r in d['datos']]")"

echo "== 5. BFF por canal (HTTPS, certificado autofirmado => -k)"
WEB=$(token web-client web123 "web.read"); MOV=$(token movil-client movil123 "movil.read")
check "BFF cajero saldo cuenta 101 => 200" 200 "$(code -k -H "Authorization: Bearer $CAJERO" https://$HOST:8094/cajero/cuentas/101/saldo)"
check "BFF web resumen => 200" 200 "$(code -k -H "Authorization: Bearer $WEB" https://$HOST:8091/web/cuentas/101/resumen)"
check "BFF movil resumen => 200" 200 "$(code -k -H "Authorization: Bearer $MOV" https://$HOST:8092/movil/cuentas/101/resumen)"
check "token web en BFF movil => 403" 403 "$(code -k -H "Authorization: Bearer $WEB" https://$HOST:8092/movil/cuentas/101/resumen)"
echo "  bytes web: $(curl -sk -H "Authorization: Bearer $WEB" https://$HOST:8091/web/cuentas/101/resumen | wc -c)  |  bytes movil: $(curl -sk -H "Authorization: Bearer $MOV" https://$HOST:8092/movil/cuentas/101/resumen | wc -c)"

echo; echo "Resultado: $ok OK, $fail FAIL"; [ "$fail" -eq 0 ]
