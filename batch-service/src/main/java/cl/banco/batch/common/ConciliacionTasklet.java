package cl.banco.batch.common;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * Control de integridad del job (conciliacion): todo registro leido debe estar escrito, filtrado u omitido.
 *
 *      leidos - filtrados - omitidos(proceso) - omitidos(escritura) == escritos
 *
 * Es el equivalente a los "totales de control" de los procesos COBOL: permite demostrar que
 * no se perdio ni se duplico informacion al migrar. El resultado queda en la tabla conciliacion_batch.
 */
@Slf4j
public class ConciliacionTasklet implements Tasklet {

    private final JdbcTemplate jdbc;
    private final boolean estricta;
    private final String jobName;
    private final String stepCarga;

    public ConciliacionTasklet(JdbcTemplate jdbc, boolean estricta, String jobName, String stepCarga) {
        this.jdbc = jdbc;
        this.estricta = estricta;
        this.jobName = jobName;
        this.stepCarga = stepCarga;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        JobExecution je = chunkContext.getStepContext().getStepExecution().getJobExecution();
        StepExecution carga = je.getStepExecutions().stream()
                .filter(s -> s.getStepName().equals(stepCarga))
                .findFirst()
                .orElse(null);
        if (carga == null) {
            // Reejecucion: el step de carga ya estaba COMPLETED en un intento anterior y no se repitio
            log.info("[BATCH] Conciliacion de {}: el step {} no se ejecuto en esta corrida, se omite", jobName, stepCarga);
            return RepeatStatus.FINISHED;
        }

        long leidos = carga.getReadCount();
        long escritos = carga.getWriteCount();
        long filtrados = carga.getFilterCount();
        long omProceso = carga.getProcessSkipCount();
        long omEscritura = carga.getWriteSkipCount();
        long omLectura = carga.getReadSkipCount();
        boolean consistente = (leidos - filtrados - omProceso - omEscritura) == escritos;

        jdbc.update("INSERT INTO conciliacion_batch (job_name, job_execution_id, leidos, escritos, filtrados, "
                        + "omitidos_proceso, omitidos_escritura, omitidos_lectura, consistente) VALUES (?,?,?,?,?,?,?,?,?)",
                jobName, je.getId(), leidos, escritos, filtrados, omProceso, omEscritura, omLectura, consistente);

        log.info("[BATCH] Conciliacion {}: leidos={} escritos={} filtrados={} omitidos(lec/proc/esc)={}/{}/{} => {}",
                jobName, leidos, escritos, filtrados, omLectura, omProceso, omEscritura,
                consistente ? "CONSISTENTE" : "INCONSISTENTE");

        if (!consistente && estricta) {
            throw new IllegalStateException("Conciliacion inconsistente en " + jobName
                    + ": los totales de control no cuadran");
        }
        return RepeatStatus.FINISHED;
    }
}
