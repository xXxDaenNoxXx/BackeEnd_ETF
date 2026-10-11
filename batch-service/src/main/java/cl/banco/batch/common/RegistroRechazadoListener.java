package cl.banco.batch.common;

import org.springframework.batch.core.SkipListener;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.jdbc.core.JdbcTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * Deja trazabilidad de CADA registro omitido (tabla registro_rechazado): nada se pierde en silencio,
 * que es lo que permite auditar la migracion contra el sistema legacy.
 */
@Slf4j
public class RegistroRechazadoListener implements SkipListener<Object, Object> {

    private final JdbcTemplate jdbc;
    private final String jobName;
    private final String stepName;

    public RegistroRechazadoListener(JdbcTemplate jdbc, String jobName, String stepName) {
        this.jdbc = jdbc;
        this.jobName = jobName;
        this.stepName = stepName;
    }

    @Override
    public void onSkipInRead(Throwable t) {
        if (t instanceof FlatFileParseException e) {
            registrar("linea " + e.getLineNumber() + ": " + e.getInput(), "Linea ilegible: " + t.getMessage());
        } else {
            registrar("(lectura)", t.getMessage());
        }
    }

    @Override
    public void onSkipInProcess(Object item, Throwable t) {
        registrar(String.valueOf(item), t.getMessage());
    }

    @Override
    public void onSkipInWrite(Object item, Throwable t) {
        registrar(String.valueOf(item), "Error de escritura: " + t.getMessage());
    }

    private void registrar(String detalle, String motivo) {
        try {
            jdbc.update("INSERT INTO registro_rechazado (job_name, step_name, detalle, motivo) VALUES (?, ?, ?, ?)",
                    jobName, stepName, recortar(detalle, 390), recortar(motivo, 290));
        } catch (Exception e) {
            // La trazabilidad nunca debe tumbar el procesamiento
            log.warn("No se pudo registrar el rechazo ({}): {}", motivo, e.getMessage());
        }
    }

    private static String recortar(String texto, int max) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= max ? texto : texto.substring(0, max);
    }
}
