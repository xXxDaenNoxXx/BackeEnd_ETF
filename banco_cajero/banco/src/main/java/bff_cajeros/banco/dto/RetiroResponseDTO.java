package bff_cajeros.banco.dto;

import java.math.BigDecimal;

public record RetiroResponseDTO(Long cuentaId, BigDecimal saldoActual, String mensaje) {
}