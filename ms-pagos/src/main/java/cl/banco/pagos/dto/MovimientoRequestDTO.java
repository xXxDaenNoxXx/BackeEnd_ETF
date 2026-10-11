package cl.banco.pagos.dto;

import java.math.BigDecimal;

public record MovimientoRequestDTO(BigDecimal monto, String referencia) {
}
