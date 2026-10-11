package bff_movil.banco.exception;

public class ServicioNoDisponibleException extends RuntimeException {
    public ServicioNoDisponibleException(String message, Throwable cause) {
        super(message, cause);
    }
}