package cl.banco.batch.estados;

import java.math.BigDecimal;

public record EstadoCuentaAnual(long cuentaId, int anio, int cantidadMovimientos, BigDecimal totalDepositos,
                                BigDecimal totalEgresos, BigDecimal saldoNeto, String estado) {
}
