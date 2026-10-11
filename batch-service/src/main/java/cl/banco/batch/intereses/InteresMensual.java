package cl.banco.batch.intereses;

import java.math.BigDecimal;

public record InteresMensual(long cuentaId, String periodo, String nombre, String tipo, BigDecimal saldo,
                             BigDecimal tasaMensual, BigDecimal interes, BigDecimal nuevoSaldo) {
}
