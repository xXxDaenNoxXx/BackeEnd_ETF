package cl.banco.cuentas.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Mismo contrato que publica ms-pagos en el topico transacciones.completadas. */
public record TransaccionCompletadaEvent(String pagoId, String tipo, Long cuentaOrigenId, Long cuentaDestinoId,
                                         BigDecimal monto, LocalDateTime fecha) {
}
