package cl.banco.clientes.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClienteRequest(
        @NotBlank @Pattern(regexp = "^[0-9]{7,8}-[0-9Kk]$", message = "RUT con formato 12345678-9") String rut,
        @NotBlank @Size(max = 120) String nombre,
        @NotBlank @Email @Size(max = 120) String email,
        @Size(max = 20) String telefono,
        @Pattern(regexp = "BASICO|PREMIUM|EMPRESA", message = "perfil debe ser BASICO, PREMIUM o EMPRESA") String perfil) {
}
