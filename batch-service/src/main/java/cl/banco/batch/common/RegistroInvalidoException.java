package cl.banco.batch.common;

/** Registro que no cumple las reglas de calidad. Es "skippable": se omite, se registra el motivo y el job continua. */
public class RegistroInvalidoException extends RuntimeException {
    public RegistroInvalidoException(String motivo) {
        super(motivo);
    }
}
