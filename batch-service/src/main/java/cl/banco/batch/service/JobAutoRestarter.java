package cl.banco.batch.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import cl.banco.batch.config.BatchProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * Politica de finalizacion y REEJECUCION AUTOMATICA ante fallos criticos:
 *
 *  1. Al arrancar: las ejecuciones que quedaron en STARTED porque el proceso murio (caida del contenedor,
 *     corte de energia) se marcan FAILED; si no, Spring Batch las consideraria "en curso" y no dejaria reiniciarlas.
 *  2. Periodicamente: toda instancia cuya ULTIMA ejecucion termino FAILED se relanza con los MISMOS parametros,
 *     lo que en Spring Batch es un "restart": los steps ya COMPLETED no se repiten y los reiniciables
 *     continuan desde el ultimo chunk confirmado.
 *  3. Tope de reintentos (batch.restart.max-restarts): superado, se deja en FAILED y se alerta en el log
 *     para intervencion manual (evita bucles infinitos con datos irrecuperables).
 *
 * Nota de escalabilidad: se asume UNA instancia del batch-service (o un lider). Para varias instancias
 * habria que agregar un lock distribuido; el escalado del procesamiento se logra con particionado, no con replicas.
 */
@Slf4j
@Component
public class JobAutoRestarter {

    private final JobExplorer jobExplorer;
    private final JobRepository jobRepository;
    private final JobRunner runner;
    private final BatchProperties props;

    public JobAutoRestarter(JobExplorer jobExplorer, JobRepository jobRepository, JobRunner runner,
                            BatchProperties props) {
        this.jobExplorer = jobExplorer;
        this.jobRepository = jobRepository;
        this.runner = runner;
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recuperarEjecucionesHuerfanas() {
        for (String job : jobExplorer.getJobNames()) {
            for (JobExecution je : jobExplorer.findRunningJobExecutions(job)) {
                log.warn("[RESTART] Ejecucion {} de {} quedo en curso tras una caida: se marca FAILED", je.getId(), job);
                for (StepExecution se : je.getStepExecutions()) {
                    if (se.getStatus().isRunning()) {
                        se.setStatus(BatchStatus.FAILED);
                        se.setExitStatus(ExitStatus.FAILED.addExitDescription("Interrumpido por caida del servicio"));
                        se.setEndTime(LocalDateTime.now());
                        jobRepository.update(se);
                    }
                }
                je.setStatus(BatchStatus.FAILED);
                je.setExitStatus(ExitStatus.FAILED.addExitDescription("Interrumpido por caida del servicio"));
                je.setEndTime(LocalDateTime.now());
                jobRepository.update(je);
            }
        }
    }

    @Scheduled(initialDelayString = "${batch.restart.interval-ms:60000}",
            fixedDelayString = "${batch.restart.interval-ms:60000}")
    public void reejecutarFallidos() {
        if (!props.getRestart().isEnabled()) {
            return;
        }
        for (String nombre : jobExplorer.getJobNames()) {
            List<JobInstance> instancias = jobExplorer.getJobInstances(nombre, 0, 10);
            for (JobInstance instancia : instancias) {
                List<JobExecution> ejecuciones = jobExplorer.getJobExecutions(instancia);
                if (ejecuciones.isEmpty()) {
                    continue;
                }
                JobExecution ultima = ejecuciones.stream().max(Comparator.comparing(JobExecution::getId)).get();
                if (ultima.getStatus() != BatchStatus.FAILED) {
                    continue;
                }
                long fallos = ejecuciones.stream().filter(e -> e.getStatus() == BatchStatus.FAILED).count();
                if (fallos > props.getRestart().getMaxRestarts()) {
                    log.error("[RESTART] {} (instancia {}) fallo {} veces: se agotaron los reintentos automaticos, "
                            + "requiere intervencion manual", nombre, instancia.getInstanceId(), fallos);
                    continue;
                }
                try {
                    log.warn("[RESTART] Reejecutando {} (instancia {}), intento {} de {}", nombre,
                            instancia.getInstanceId(), fallos, props.getRestart().getMaxRestarts());
                    runner.getJobLauncher().run(runner.buscar(nombre), ultima.getJobParameters());
                } catch (Exception e) {
                    log.error("[RESTART] No se pudo reejecutar {}: {}", nombre, e.getMessage());
                }
            }
        }
    }
}
