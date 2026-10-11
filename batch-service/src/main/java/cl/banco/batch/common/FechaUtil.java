package cl.banco.batch.common;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;

/** Los CSV legacy mezclan 4 formatos de fecha. Se prueban en orden; si ninguno calza, la fecha es invalida. */
public final class FechaUtil {

    private static final List<DateTimeFormatter> FORMATOS = List.of(
            formato("uuuu-MM-dd"),
            formato("uuuu/MM/dd"),
            formato("dd-MM-uuuu"),
            formato("dd/MM/uuuu"));

    private FechaUtil() {
    }

    private static DateTimeFormatter formato(String patron) {
        return DateTimeFormatter.ofPattern(patron).withResolverStyle(ResolverStyle.STRICT);
    }

    /** @return la fecha o null si esta vacia / no calza con ningun formato */
    public static LocalDate parsear(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        for (DateTimeFormatter f : FORMATOS) {
            try {
                return LocalDate.parse(texto.trim(), f);
            } catch (DateTimeParseException ignorado) {
                // se prueba el siguiente formato
            }
        }
        return null;
    }
}
