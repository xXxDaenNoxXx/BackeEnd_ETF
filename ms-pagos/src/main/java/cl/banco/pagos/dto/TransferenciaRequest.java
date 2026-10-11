package cl.banco.pagos.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransferenciaRequest(
        @NotNull Long cuentaOrigenId,
        @NotNull Long cuentaDestinoId,
        @NotNull @DecimalMin(value = "0.01", message = "monto debe ser mayor a 0") BigDecimal monto,
        @Size(max = 120) String descripcion) {
}
