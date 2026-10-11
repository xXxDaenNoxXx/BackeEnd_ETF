package bff_cajeros.banco.dto;

import java.math.BigDecimal;

// Solo lo esencial: NADA de nombre, edad ni tipo de cuenta
public record CuentaCajeroSaldoDTO(Long cuentaId, BigDecimal saldo) {
}