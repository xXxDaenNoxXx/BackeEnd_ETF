package cl.banco.cuentas.exception;

/** Una dependencia (ms-clientes) no responde: circuito abierto o reintentos agotados (HTTP 503). */
public class ServicioNoDisponibleException extends RuntimeException {
    public ServicioNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
