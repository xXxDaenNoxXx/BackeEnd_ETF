package bff_web.banco.dto;

import java.math.BigDecimal;

public record CuentaWebDTO(Long cuentaId, String titular, String tipo, Integer edad, BigDecimal saldo) {
}
