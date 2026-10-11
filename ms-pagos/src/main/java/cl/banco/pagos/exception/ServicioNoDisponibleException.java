package cl.banco.pagos.exception;

/** ms-cuentas no responde: circuito abierto o reintentos agotados. */
public class ServicioNoDisponibleException extends RuntimeException {
    public ServicioNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
