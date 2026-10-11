package bff_cajeros.banco.config;

import java.io.IOException;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Agrega "Authorization: Bearer <token>" a cada llamada hacia ms-core-banco.
 * Si auth-server no responde, la excepcion sube hasta @Retry/@CircuitBreaker
 * de MsCoreClient y se maneja como cualquier otra falla del servicio.
 */
@Component
@RequiredArgsConstructor
public class OAuth2BearerInterceptor implements ClientHttpRequestInterceptor {

    private static final String REGISTRATION_ID = "core";

    private final OAuth2AuthorizedClientManager authorizedClientManager;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {

        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId(REGISTRATION_ID)
                .principal("bff-cajeros")
                .build();

        OAuth2AuthorizedClient client = authorizedClientManager.authorize(authorizeRequest);
        if (client == null) {
            throw new IllegalStateException("No se pudo obtener el token OAuth2 para llamar a ms-core-banco");
        }
        request.getHeaders().setBearerAuth(client.getAccessToken().getTokenValue());
        return execution.execute(request, body);
    }
}
