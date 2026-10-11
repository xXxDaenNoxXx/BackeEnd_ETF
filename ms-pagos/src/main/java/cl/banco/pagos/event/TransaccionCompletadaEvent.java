package cl.banco.pagos.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Topico transacciones.completadas. Lo consume ms-cuentas (actividad por cuenta). */
public record TransaccionCompletadaEvent(String pagoId, String tipo, Long cuentaOrigenId, Long cuentaDestinoId,
                                         BigDecimal monto, LocalDateTime fecha) {
}
