package cl.banco.batch.estados;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Movimiento validado y normalizado (monto siempre positivo). */
public record MovimientoAnual(long cuentaId, LocalDate fecha, String transaccion, BigDecimal monto,
                              String descripcion) {
}
