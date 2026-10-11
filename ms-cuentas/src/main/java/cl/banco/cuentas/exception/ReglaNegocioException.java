package cl.banco.cuentas.exception;

/** Violacion de una regla de negocio (HTTP 422): saldo insuficiente, cuenta cerrada, etc. */
public class ReglaNegocioException extends RuntimeException {
    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
