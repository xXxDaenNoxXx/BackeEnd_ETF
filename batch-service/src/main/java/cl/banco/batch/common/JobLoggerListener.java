package cl.banco.batch.common;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;

import lombok.extern.slf4j.Slf4j;

/** Registra inicio/fin de cada job y un resumen por step (leidos, escritos, omitidos...) para monitoreo. */
@Slf4j
public class JobLoggerListener implements JobExecutionListener {

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("[BATCH] Inicia {} (ejecucion {}) parametros={}",
                jobExecution.getJobInstance().getJobName(), jobExecution.getId(), jobExecution.getJobParameters());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        log.info("[BATCH] Termina {} (ejecucion {}) estado={}",
                jobExecution.getJobInstance().getJobName(), jobExecution.getId(), jobExecution.getStatus());
        for (StepExecution s : jobExecution.getStepExecutions()) {
            log.info("[BATCH]   step={} estado={} leidos={} escritos={} filtrados={} omitidos(lec/proc/esc)={}/{}/{} commits={} rollbacks={}",
                    s.getStepName(), s.getStatus(), s.getReadCount(), s.getWriteCount(), s.getFilterCount(),
                    s.getReadSkipCount(), s.getProcessSkipCount(), s.getWriteSkipCount(),
                    s.getCommitCount(), s.getRollbackCount());
        }
        if (!jobExecution.getAllFailureExceptions().isEmpty()) {
            log.error("[BATCH] {} fallo: {}", jobExecution.getJobInstance().getJobName(),
                    jobExecution.getAllFailureExceptions().get(0).getMessage());
        }
    }
}
