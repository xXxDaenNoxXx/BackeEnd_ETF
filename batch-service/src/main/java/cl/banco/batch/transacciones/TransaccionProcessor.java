package cl.banco.batch.transacciones;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.batch.item.ItemProcessor;

import cl.banco.batch.common.FechaUtil;

/**
 * Regla del legacy "detectar anomalias": una transaccion es ANOMALA si tiene fecha invalida, monto faltante,
 * no numerico, cero o negativo, o un tipo distinto de debito/credito.
 * Las anomalias NO se descartan: se guardan marcadas con su motivo para que auditoria las revise.
 * Solo se omite (skip) la linea cuyo id no es numerico, porque no hay clave para guardarla.
 * Es stateless => seguro para el step multi-hilo.
 */
public class TransaccionProcessor implements ItemProcessor<TransaccionCsv, TransaccionProcesada> {

    private static final Set<String> TIPOS_VALIDOS = Set.of("debito", "credito");

    @Override
    public TransaccionProcesada process(TransaccionCsv t) {
        long id = Long.parseLong(t.id().trim()); // NumberFormatException => skip

        List<String> motivos = new ArrayList<>();

        LocalDate fecha = FechaUtil.parsear(t.fecha());
        if (fecha == null) {
            motivos.add("fecha invalida");
        }

        BigDecimal monto = null;
        if (t.monto() == null || t.monto().isBlank()) {
            motivos.add("monto faltante");
        } else {
            try {
                monto = new BigDecimal(t.monto().trim());
                if (monto.signum() < 0) {
                    motivos.add("monto negativo");
                } else if (monto.signum() == 0) {
                    motivos.add("monto cero");
                }
            } catch (NumberFormatException e) {
                motivos.add("monto no numerico");
            }
        }

        String tipo = t.tipo() == null ? "" : t.tipo().trim().toLowerCase();
        if (!TIPOS_VALIDOS.contains(tipo)) {
            motivos.add("tipo invalido: " + tipo);
        }

        boolean valida = motivos.isEmpty();
        return new TransaccionProcesada(id, fecha, monto, tipo,
                valida ? "VALIDA" : "ANOMALA", valida ? null : String.join("; ", motivos));
    }
}
