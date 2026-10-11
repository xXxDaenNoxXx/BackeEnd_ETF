package cl.banco.cuentas.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AbrirCuentaRequest(
        @NotNull Long clienteId,
        @NotNull @Pattern(regexp = "CORRIENTE|AHORRO|VISTA", message = "tipo debe ser CORRIENTE, AHORRO o VISTA") String tipo,
        @Size(max = 60) String alias,
        @DecimalMin(value = "0.00", message = "saldoInicial no puede ser negativo") BigDecimal saldoInicial) {
}
