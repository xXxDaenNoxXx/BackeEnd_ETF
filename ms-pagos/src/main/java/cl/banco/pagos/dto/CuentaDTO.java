package cl.banco.pagos.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Respuesta de ms-cuentas (solo los campos que ms-pagos usa). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CuentaDTO(Long id, Long clienteId, BigDecimal saldo, String estado) {
}
