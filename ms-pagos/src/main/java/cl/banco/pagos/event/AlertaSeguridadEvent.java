package cl.banco.pagos.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Topico alertas.seguridad. Lo consume ms-clientes (se asocia al cliente afectado). */
public record AlertaSeguridadEvent(String tipo, Long clienteId, Long cuentaId, BigDecimal monto, String pagoId,
                                   String mensaje, LocalDateTime fecha) {
}
