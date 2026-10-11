package config_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Configuracion centralizada (Spring Cloud Config).
 * Perfil "native": los archivos viven en classpath:/config-repo (dentro de la imagen).
 * En produccion se cambia a un repositorio Git (spring.cloud.config.server.git.uri) sin tocar los servicios.
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
