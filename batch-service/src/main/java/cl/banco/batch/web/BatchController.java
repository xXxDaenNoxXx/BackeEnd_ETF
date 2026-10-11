package cl.banco.batch.web;

import java.util.List;
import java.util.Map;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cl.banco.batch.service.JobRunner;
import lombok.RequiredArgsConstructor;

/**
 * API operativa del batch (scope batch.run):
 *   POST /batch/jobs/{alias}            lanza el job (202 Accepted; corre en segundo plano)
 *   GET  /batch/jobs/{alias}/ultima     estado y contadores por step de la ultima ejecucion
 *   GET  /batch/reportes/{nombre}       resultados (transacciones | intereses | estados | rechazados | conciliacion)
 */
@RestController
@RequestMapping("/batch")
@RequiredArgsConstructor
public class BatchController {

    private static final Map<String, String> REPORTES = Map.of(
            "transacciones", "SELECT * FROM resumen_transacciones_diarias ORDER BY fecha",
            "anomalias", "SELECT * FROM transaccion_procesada WHERE estado = 'ANOMALA' ORDER BY id",
            "intereses", "SELECT * FROM interes_mensual ORDER BY periodo, cuenta_id",
            "estados", "SELECT * FROM estado_cuenta_anual ORDER BY cuenta_id, anio",
            "rechazados", "SELECT * FROM registro_rechazado ORDER BY id",
            "conciliacion", "SELECT * FROM conciliacion_batch ORDER BY id DESC");

    private final JobRunner runner;
    private final JobExplorer jobExplorer;
    private final JdbcTemplate jdbc;

    public record EjecucionDTO(Long ejecucionId, String job, String estado) {
    }

    public record StepDTO(String step, String estado, long leidos, long escritos, long filtrados,
                          long omitidosLectura, long omitidosProceso, long omitidosEscritura,
                          long commits, long rollbacks) {
    }

    public record DetalleEjecucionDTO(Long ejecucionId, String job, String estado, String salida,
                                      Object inicio, Object fin, List<StepDTO> steps) {
    }

    @PostMapping("/jobs/{alias}")
    public ResponseEntity<?> lanzar(@PathVariable String alias) throws Exception {
        if (!JobRunner.ALIAS.containsKey(alias)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Job desconocido", "validos", JobRunner.ALIAS.keySet()));
        }
        JobExecution je = runner.lanzar(alias);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new EjecucionDTO(je.getId(), je.getJobInstance().getJobName(), je.getStatus().toString()));
    }

    @GetMapping("/jobs/{alias}/ultima")
    public ResponseEntity<?> ultima(@PathVariable String alias) {
        String nombre = JobRunner.ALIAS.get(alias);
        if (nombre == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Job desconocido", "validos", JobRunner.ALIAS.keySet()));
        }
        JobInstance instancia = jobExplorer.getLastJobInstance(nombre);
        if (instancia == null) {
            return ResponseEntity.notFound().build();
        }
        JobExecution je = jobExplorer.getLastJobExecution(instancia);
        if (je == null) {
            return ResponseEntity.notFound().build();
        }
        List<StepDTO> steps = je.getStepExecutions().stream()
                .map(s -> new StepDTO(s.getStepName(), s.getStatus().toString(), s.getReadCount(), s.getWriteCount(),
                        s.getFilterCount(), s.getReadSkipCount(), s.getProcessSkipCount(), s.getWriteSkipCount(),
                        s.getCommitCount(), s.getRollbackCount()))
                .toList();
        return ResponseEntity.ok(new DetalleEjecucionDTO(je.getId(), nombre, je.getStatus().toString(),
                je.getExitStatus().getExitCode(), je.getStartTime(), je.getEndTime(), steps));
    }

    @GetMapping("/reportes/{nombre}")
    public ResponseEntity<?> reporte(@PathVariable String nombre,
                                     @RequestParam(defaultValue = "200") int limite) {
        String sql = REPORTES.get(nombre);
        if (sql == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Reporte desconocido", "validos", REPORTES.keySet()));
        }
        int tope = Math.max(1, Math.min(limite, 1000));
        List<Map<String, Object>> filas = jdbc.queryForList(sql + " LIMIT " + tope);
        return ResponseEntity.ok(Map.of("reporte", nombre, "filas", filas.size(), "datos", filas));
    }
}
