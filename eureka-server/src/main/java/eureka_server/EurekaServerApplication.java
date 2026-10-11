package eureka_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Registro y descubrimiento de servicios (Eureka).
 * Los microservicios se registran aqui y se encuentran por NOMBRE (lb://ms-pagos),
 * lo que permite agregar o quitar instancias sin cambiar configuracion.
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
