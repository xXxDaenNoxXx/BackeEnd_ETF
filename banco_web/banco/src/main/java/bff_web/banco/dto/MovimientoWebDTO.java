package bff_web.banco.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MovimientoWebDTO(Long id, LocalDate fecha, String transaccion, BigDecimal monto, String descripcion) {
}
