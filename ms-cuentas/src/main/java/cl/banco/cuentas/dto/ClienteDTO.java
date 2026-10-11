package cl.banco.cuentas.dto;

/** Lo minimo que ms-cuentas necesita saber de ms-clientes (se ignoran los demas campos). */
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public record ClienteDTO(Long id, String rut, String nombre, String estado) {
}
