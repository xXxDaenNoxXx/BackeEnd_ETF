package cl.banco.cuentas.config;

import java.time.Duration;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import lombok.RequiredArgsConstructor;

/**
 * RestTemplate con BALANCEO DE CARGA: las URLs usan el nombre logico ("http://ms-clientes/...") y
 * Spring Cloud LoadBalancer elige una instancia registrada en Eureka (round-robin).
 * Timeouts cortos: es mejor fallar rapido y dejar actuar a Resilience4j que bloquear hilos.
 */
@Configuration
@RequiredArgsConstructor
public class HttpClientConfig {

    private final OAuth2BearerInterceptor bearerInterceptor;

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .additionalInterceptors(bearerInterceptor)
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(4))
                .build();
    }
}
