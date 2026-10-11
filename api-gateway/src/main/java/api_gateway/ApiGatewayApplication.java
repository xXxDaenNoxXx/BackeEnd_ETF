package api_gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto unico de entrada a los microservicios de negocio.
 * Enruta por prefijo de ruta a "lb://<nombre-del-servicio>": el balanceador de carga del lado
 * del cliente (Spring Cloud LoadBalancer) reparte las llamadas entre las instancias registradas en Eureka.
 * El gateway NO valida tokens: reenvia el header Authorization y cada microservicio
 * (OAuth2 Resource Server) valida el JWT y los scopes (defensa en profundidad).
 */
@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
