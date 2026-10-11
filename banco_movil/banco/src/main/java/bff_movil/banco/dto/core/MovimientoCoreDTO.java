package bff_movil.banco.dto.core;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MovimientoCoreDTO(Long id, Long cuentaId, LocalDate fecha, String transaccion, BigDecimal monto,
                                String descripcion) {
}
