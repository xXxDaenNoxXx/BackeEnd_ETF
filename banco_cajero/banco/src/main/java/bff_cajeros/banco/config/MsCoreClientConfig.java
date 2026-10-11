package bff_cajeros.banco.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class MsCoreClientConfig {

    @Value("${core.base-url}")
    private String baseUrl;

    private final OAuth2BearerInterceptor oauth2BearerInterceptor;

    @SuppressWarnings("null")
    @Bean
    public RestClient coreRestClient() {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestInterceptor(oauth2BearerInterceptor) // token OAuth2 en cada llamada al core
                .build();
    }
}
