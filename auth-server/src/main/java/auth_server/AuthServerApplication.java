package auth_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Servidor de autorizacion OAuth2.0 del Banco XYZ.
 * Emite tokens JWT (flujo client_credentials) firmados con RSA.
 * La configuracion de clientes y scopes esta en application.properties.
 */
@SpringBootApplication
public class AuthServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServerApplication.class, args);
    }
}
