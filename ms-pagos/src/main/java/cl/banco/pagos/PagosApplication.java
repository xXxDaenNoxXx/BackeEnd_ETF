package cl.banco.pagos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Microservicio de Procesamiento de Pagos: pagos, transferencias y depositos. Base propia: pagos_db. */
@SpringBootApplication
@EnableScheduling
public class PagosApplication {
    public static void main(String[] args) {
        SpringApplication.run(PagosApplication.class, args);
    }
}
