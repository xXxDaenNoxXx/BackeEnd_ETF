package cl.banco.cuentas.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import cl.banco.cuentas.model.Cuenta;

public record CuentaResponse(Long id, String numero, Long clienteId, String tipo, String alias, BigDecimal saldo,
                             String estado, LocalDateTime creadaEn) {

    public static CuentaResponse de(Cuenta c) {
        return new CuentaResponse(c.getId(), c.getNumero(), c.getClienteId(), c.getTipo(), c.getAlias(),
                c.getSaldo(), c.getEstado(), c.getCreadaEn());
    }
}
