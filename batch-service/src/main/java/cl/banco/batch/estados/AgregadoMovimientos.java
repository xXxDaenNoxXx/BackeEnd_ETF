package cl.banco.batch.estados;

import java.math.BigDecimal;

/** Totales de una cuenta en un anio, leidos con GROUP BY desde movimiento_anual. */
public record AgregadoMovimientos(long cuentaId, int anio, int cantidad, BigDecimal depositos, BigDecimal egresos) {
}
