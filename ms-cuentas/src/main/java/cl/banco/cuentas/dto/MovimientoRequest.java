package cl.banco.cuentas.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Debito o credito. 'referencia' identifica la operacion de origen y garantiza idempotencia. */
public record MovimientoRequest(
        @NotNull @DecimalMin(value = "0.01", message = "monto debe ser mayor a 0") BigDecimal monto,
        @NotBlank @Size(max = 80) String referencia) {
}
