package cl.banco.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import cl.banco.batch.config.BatchProperties;

/**
 * Migracion de los procesos batch legacy (COBOL + scripts Shell sobre mainframe) a Spring Batch.
 *   1. Reporte de Transacciones Diarias   -> reporteTransaccionesDiariasJob
 *   2. Calculo de Intereses Mensuales     -> calculoInteresesMensualesJob
 *   3. Estados de Cuenta Anuales          -> estadosCuentaAnualesJob
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(BatchProperties.class)
public class BatchServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BatchServiceApplication.class, args);
    }
}
