package bff_web.banco.dto;

import java.math.BigDecimal;
import java.util.List;

/** Vista completa para interfaces web complejas: cuenta + historial completo + totales ya calculados. */
public record ResumenWebDTO(CuentaWebDTO cuenta, List<MovimientoWebDTO> movimientos, Totales totales) {

    public record Totales(int cantidadMovimientos, BigDecimal totalDepositos, BigDecimal totalEgresos,
                          BigDecimal balanceNeto) {
    }
}
