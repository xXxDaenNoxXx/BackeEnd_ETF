package bff_movil.banco.dto;

import java.math.BigDecimal;
import java.util.List;

/** Payload MINIMO para app movil: saldo y los ultimos 5 movimientos con 3 campos cada uno. */
public record ResumenMovilDTO(Long cuentaId, BigDecimal saldo, List<Mov> ultimos) {

    public record Mov(String fecha, String tipo, BigDecimal monto) {
    }
}
