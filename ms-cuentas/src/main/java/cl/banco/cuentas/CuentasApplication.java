package cl.banco.cuentas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Microservicio de Gestion de Cuentas: apertura, cierre y mantenimiento. Base propia: cuentas_db. */
@SpringBootApplication
public class CuentasApplication {
    public static void main(String[] args) {
        SpringApplication.run(CuentasApplication.class, args);
    }
}
