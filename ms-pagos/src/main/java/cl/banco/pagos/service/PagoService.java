package cl.banco.pagos.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cl.banco.pagos.client.CuentaClient;
import cl.banco.pagos.dto.CuentaDTO;
import cl.banco.pagos.dto.DepositoRequest;
import cl.banco.pagos.dto.PagoRequest;
import cl.banco.pagos.dto.TransferenciaRequest;
import cl.banco.pagos.event.AlertaSeguridadEvent;
import cl.banco.pagos.event.EventPublisher;
import cl.banco.pagos.event.TransaccionCompletadaEvent;
import cl.banco.pagos.exception.RecursoNoEncontradoException;
import cl.banco.pagos.exception.ServicioNoDisponibleException;
import cl.banco.pagos.model.Pago;
import cl.banco.pagos.repository.PagoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orquesta pagos, transferencias y depositos sobre ms-cuentas con el patron SAGA (consistencia eventual):
 *
 *   transferencia = debitar(origen) -> acreditar(destino)
 *   si acreditar falla  => compensacion: acreditar(origen) con referencia ":R" (reverso)
 *   si el reverso falla => estado PENDIENTE_COMPENSACION y un scheduler lo reintenta hasta lograrlo
 *
 * No hay transaccion distribuida (2PC): cada paso es local a un servicio, idempotente por "referencia"
 * (idPago:D / :C / :R) y el estado de la saga queda persistido en pagos_db.
 * Este servicio NO es @Transactional a proposito: no se debe mantener una transaccion abierta durante llamadas remotas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PagoService {

    public record Resultado(Pago pago, HttpStatus status) {
    }

    private final PagoRepository pagoRepository;
    private final CuentaClient cuentaClient;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Value("${pagos.topic-transacciones}")
    private String topicoTransacciones;

    @Value("${pagos.topic-alertas}")
    private String topicoAlertas;

    @Value("${pagos.umbral-alerta-monto}")
    private BigDecimal umbralAlerta;

    // ------------------------------------------------------------------ operaciones
    public Resultado deposito(DepositoRequest r, String clave) {
        Optional<Pago> previo = pagoRepository.findById(clave);
        if (previo.isPresent()) {
            return repetido(previo.get());
        }
        Pago p = nuevo(clave, "DEPOSITO", null, r.cuentaId(), r.monto(), r.descripcion());
        try {
            CuentaDTO cuenta = cuentaClient.acreditar(r.cuentaId(), r.monto(), clave + ":C");
            return completar(p, cuenta);
        } catch (HttpClientErrorException e) {
            return rechazar(p, estado(e), mensaje(e));
        } catch (ServicioNoDisponibleException e) {
            return rechazar(p, HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
    }

    public Resultado pago(PagoRequest r, String clave) {
        Optional<Pago> previo = pagoRepository.findById(clave);
        if (previo.isPresent()) {
            return repetido(previo.get());
        }
        Pago p = nuevo(clave, "PAGO", r.cuentaId(), null, r.monto(), r.descripcion());
        try {
            CuentaDTO cuenta = cuentaClient.debitar(r.cuentaId(), r.monto(), clave + ":D");
            return completar(p, cuenta);
        } catch (HttpClientErrorException e) {
            return rechazar(p, estado(e), mensaje(e));
        } catch (ServicioNoDisponibleException e) {
            return rechazar(p, HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
    }

    public Resultado transferencia(TransferenciaRequest r, String clave) {
        Optional<Pago> previo = pagoRepository.findById(clave);
        if (previo.isPresent()) {
            return repetido(previo.get());
        }
        Pago p = nuevo(clave, "TRANSFERENCIA", r.cuentaOrigenId(), r.cuentaDestinoId(), r.monto(), r.descripcion());
        if (r.cuentaOrigenId().equals(r.cuentaDestinoId())) {
            return rechazar(p, HttpStatus.UNPROCESSABLE_ENTITY, "La cuenta de origen y destino no pueden ser la misma");
        }

        // Paso 1: debitar origen
        CuentaDTO origen;
        try {
            origen = cuentaClient.debitar(r.cuentaOrigenId(), r.monto(), clave + ":D");
        } catch (HttpClientErrorException e) {
            return rechazar(p, estado(e), mensaje(e));
        } catch (ServicioNoDisponibleException e) {
            return rechazar(p, HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }

        // Paso 2: acreditar destino (si falla => compensar el paso 1)
        try {
            cuentaClient.acreditar(r.cuentaDestinoId(), r.monto(), clave + ":C");
            return completar(p, origen);
        } catch (HttpClientErrorException e) {
            return compensar(p, estado(e), "Transferencia revertida: " + mensaje(e));
        } catch (ServicioNoDisponibleException e) {
            return compensar(p, HttpStatus.SERVICE_UNAVAILABLE, "Transferencia revertida: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ consultas
    public Pago obtener(String id) {
        return pagoRepository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("No existe el pago " + id));
    }

    public List<Pago> porCuenta(Long cuentaId) {
        return pagoRepository.findByCuentaOrigenIdOrCuentaDestinoIdOrderByCreadoEnDesc(cuentaId, cuentaId);
    }

    // ------------------------------------------------------------------ reintento de compensaciones pendientes
    /** Lo invoca el scheduler: reintenta el reverso (idempotente) de las transferencias que quedaron a medias. */
    public void reintentarCompensacionesPendientes() {
        for (Pago p : pagoRepository.findByEstado("PENDIENTE_COMPENSACION")) {
            try {
                cuentaClient.acreditar(p.getCuentaOrigenId(), p.getMonto(), p.getId() + ":R");
                p.setEstado("COMPENSADO");
                p.setActualizadoEn(LocalDateTime.now());
                pagoRepository.save(p);
                log.info("Compensacion pendiente completada para el pago {}", p.getId());
            } catch (Exception e) {
                log.warn("La compensacion del pago {} sigue pendiente: {}", p.getId(), e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ internos
    private Pago nuevo(String id, String tipo, Long origen, Long destino, BigDecimal monto, String descripcion) {
        Pago p = new Pago();
        p.setId(id);
        p.setTipo(tipo);
        p.setCuentaOrigenId(origen);
        p.setCuentaDestinoId(destino);
        p.setMonto(monto);
        p.setDescripcion(descripcion);
        p.setEstado("PENDIENTE");
        p.setCreadoEn(LocalDateTime.now());
        try {
            return pagoRepository.save(p);
        } catch (DataIntegrityViolationException e) {
            // otra replica registro la misma clave de idempotencia al mismo tiempo
            return pagoRepository.findById(id).orElseThrow(() -> e);
        }
    }

    private Resultado repetido(Pago p) {
        HttpStatus status = switch (p.getEstado()) {
            case "COMPLETADO" -> HttpStatus.OK;
            case "PENDIENTE" -> HttpStatus.ACCEPTED;
            case "PENDIENTE_COMPENSACION", "COMPENSADO", "RECHAZADO" -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.OK;
        };
        return new Resultado(p, status);
    }

    private Resultado completar(Pago p, CuentaDTO cuentaAfectada) {
        p.setEstado("COMPLETADO");
        p.setActualizadoEn(LocalDateTime.now());
        pagoRepository.save(p);

        Long clave = p.getCuentaOrigenId() != null ? p.getCuentaOrigenId() : p.getCuentaDestinoId();
        eventPublisher.publicar(topicoTransacciones, String.valueOf(clave), new TransaccionCompletadaEvent(
                p.getId(), p.getTipo(), p.getCuentaOrigenId(), p.getCuentaDestinoId(), p.getMonto(), LocalDateTime.now()));

        if (p.getMonto().compareTo(umbralAlerta) >= 0) {
            Long clienteId = cuentaAfectada == null ? null : cuentaAfectada.clienteId();
            eventPublisher.publicar(topicoAlertas, String.valueOf(clave), new AlertaSeguridadEvent(
                    "MONTO_ALTO", clienteId, clave, p.getMonto(), p.getId(),
                    p.getTipo() + " por un monto sobre el umbral de seguridad (" + umbralAlerta + ")", LocalDateTime.now()));
        }
        return new Resultado(p, HttpStatus.OK);
    }

    private Resultado rechazar(Pago p, HttpStatus status, String motivo) {
        p.setEstado("RECHAZADO");
        p.setMotivo(recortar(motivo));
        p.setActualizadoEn(LocalDateTime.now());
        pagoRepository.save(p);
        return new Resultado(p, status);
    }

    private Resultado compensar(Pago p, HttpStatus status, String motivo) {
        p.setMotivo(recortar(motivo));
        p.setActualizadoEn(LocalDateTime.now());
        try {
            cuentaClient.acreditar(p.getCuentaOrigenId(), p.getMonto(), p.getId() + ":R");
            p.setEstado("COMPENSADO");
        } catch (Exception e) {
            log.error("No se pudo revertir el debito del pago {} (queda PENDIENTE_COMPENSACION): {}", p.getId(), e.getMessage());
            p.setEstado("PENDIENTE_COMPENSACION");
        }
        pagoRepository.save(p);
        return new Resultado(p, status);
    }

    private HttpStatus estado(HttpClientErrorException e) {
        return HttpStatus.valueOf(e.getStatusCode().value());
    }

    private String mensaje(HttpClientErrorException e) {
        try {
            JsonNode n = objectMapper.readTree(e.getResponseBodyAsString());
            if (n.hasNonNull("error")) {
                return n.get("error").asText();
            }
        } catch (Exception ignorada) {
            // cuerpo no JSON
        }
        return "Rechazado por ms-cuentas (" + e.getStatusCode().value() + ")";
    }

    private String recortar(String s) {
        return s == null ? null : (s.length() <= 240 ? s : s.substring(0, 240));
    }

    public static String claveOGenerada(String cabecera) {
        return (cabecera == null || cabecera.isBlank()) ? UUID.randomUUID().toString() : cabecera.trim();
    }
}
