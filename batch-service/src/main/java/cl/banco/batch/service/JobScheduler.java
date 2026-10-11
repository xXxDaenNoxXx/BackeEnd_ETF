package cl.banco.batch.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Reemplaza al cron del mainframe: cada proceso corre en su frecuencia original (expresiones en application/config). */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "batch.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JobScheduler {

    private final JobRunner runner;

    @Scheduled(cron = "${batch.scheduling.transacciones:0 0 1 * * *}")
    public void transaccionesDiarias() {
        lanzar("transacciones");
    }

    @Scheduled(cron = "${batch.scheduling.intereses:0 0 2 1 * *}")
    public void interesesMensuales() {
        lanzar("intereses");
    }

    @Scheduled(cron = "${batch.scheduling.estados:0 0 3 1 1 *}")
    public void estadosAnuales() {
        lanzar("estados");
    }

    private void lanzar(String alias) {
        try {
            var ejecucion = runner.lanzar(alias);
            log.info("[SCHEDULER] Job {} lanzado (ejecucion {})", alias, ejecucion.getId());
        } catch (Exception e) {
            log.error("[SCHEDULER] No se pudo lanzar el job {}: {}", alias, e.getMessage(), e);
        }
    }
}
