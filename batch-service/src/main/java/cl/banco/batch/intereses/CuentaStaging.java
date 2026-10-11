package cl.banco.batch.intereses;

import java.math.BigDecimal;

/** Cuenta ya validada (tabla cuenta_staging), lista para calcular su interes. */
public record CuentaStaging(long cuentaId, String nombre, BigDecimal saldo, int edad, String tipo) {
}
