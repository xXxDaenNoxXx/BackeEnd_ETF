package cl.banco.cuentas.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Mantenimiento: solo alias y tipo son modificables (el saldo solo cambia con movimientos). */
public record MantenerCuentaRequest(
        @Size(max = 60) String alias,
        @Pattern(regexp = "CORRIENTE|AHORRO|VISTA", message = "tipo debe ser CORRIENTE, AHORRO o VISTA") String tipo) {
}
