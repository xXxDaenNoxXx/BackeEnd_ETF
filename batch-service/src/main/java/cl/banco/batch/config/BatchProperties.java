package cl.banco.batch.config;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/** Parametros del batch. Los valores de negocio vienen del Config Server (config-repo/batch-service.yml). */
@Data
@ConfigurationProperties(prefix = "batch")
public class BatchProperties {

    /** Tasa de interes MENSUAL por tipo de cuenta (ahorro, prestamo, hipoteca). */
    private Map<String, BigDecimal> interes = new HashMap<>();
    private int chunkSize = 100;
    /** Maximo de registros rechazados tolerados por step; si se supera, el job FALLA y queda reejecutable. */
    private int skipLimit = 900;
    private int retryLimit = 3;
    /** Hilos del step multi-hilo (jobs 1 y 3). */
    private int threads = 4;
    /** Particiones del job 2. */
    private int partitions = 4;
    private String outputDir = "./output";
    /** true => una conciliacion inconsistente hace fallar el job. */
    private boolean conciliacionEstricta = false;

    private Restart restart = new Restart();
    private Scheduling scheduling = new Scheduling();

    @Data
    public static class Restart {
        private boolean enabled = true;
        private long intervalMs = 60000;
        private int maxRestarts = 3;
    }

    @Data
    public static class Scheduling {
        private boolean enabled = true;
        /** Diario 01:00 */
        private String transacciones = "0 0 1 * * *";
        /** Dia 1 de cada mes 02:00 */
        private String intereses = "0 0 2 1 * *";
        /** 1 de enero 03:00 */
        private String estados = "0 0 3 1 1 *";
    }
}
