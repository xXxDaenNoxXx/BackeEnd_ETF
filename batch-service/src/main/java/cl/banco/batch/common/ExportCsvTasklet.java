package cl.banco.batch.common;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * Exporta el resultado de una consulta a un archivo CSV (el "reporte" que antes producia el mainframe).
 * Los alias de columna del SELECT deben coincidir con los nombres de columna indicados.
 */
@Slf4j
public class ExportCsvTasklet implements Tasklet {

    private final JdbcTemplate jdbc;
    private final String sql;
    private final Path destino;
    private final String[] columnas;

    public ExportCsvTasklet(JdbcTemplate jdbc, String sql, Path destino, String... columnas) {
        this.jdbc = jdbc;
        this.sql = sql;
        this.destino = destino;
        this.columnas = columnas;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        if (destino.getParent() != null) {
            Files.createDirectories(destino.getParent());
        }
        List<Map<String, Object>> filas = jdbc.queryForList(sql);
        try (BufferedWriter w = Files.newBufferedWriter(destino, StandardCharsets.UTF_8)) {
            w.write(String.join(",", columnas));
            w.newLine();
            for (Map<String, Object> fila : filas) {
                StringBuilder linea = new StringBuilder();
                for (int i = 0; i < columnas.length; i++) {
                    if (i > 0) {
                        linea.append(',');
                    }
                    linea.append(escapar(fila.get(columnas[i])));
                }
                w.write(linea.toString());
                w.newLine();
            }
        }
        contribution.incrementWriteCount(filas.size());
        log.info("[BATCH] Reporte generado: {} ({} filas)", destino.toAbsolutePath(), filas.size());
        return RepeatStatus.FINISHED;
    }

    private static String escapar(Object valor) {
        if (valor == null) {
            return "";
        }
        String s = String.valueOf(valor);
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
