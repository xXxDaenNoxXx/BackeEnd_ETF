package cl.banco.cuentas.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import cl.banco.cuentas.dto.ClienteDTO;
import cl.banco.cuentas.exception.ServicioNoDisponibleException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;

/**
 * Llamada resiliente a ms-clientes (validar que el cliente existe y esta activo).
 *  - Retry (3 intentos, backoff exponencial) ante fallos transitorios
 *  - CircuitBreaker: tras demasiados fallos deja de llamar y responde rapido
 *  - Fallback: ms-clientes no disponible => ServicioNoDisponibleException (HTTP 503 con Retry-After),
 *    nunca una cuenta abierta sin validar al titular. Los 4xx (cliente inexistente) son errores de NEGOCIO y se propagan.
 */
@Component
@RequiredArgsConstructor
public class ClienteClient {

    private final RestTemplate restTemplate;

    // Orden real de Resilience4j: Retry envuelve al CircuitBreaker, por eso el fallback va en @Retry (el mas externo).
    @Retry(name = "msClientes", fallbackMethod = "obtenerFallback")
    @CircuitBreaker(name = "msClientes")
    public ClienteDTO obtener(Long clienteId) {
        return restTemplate.getForObject("http://ms-clientes/clientes/{id}", ClienteDTO.class, clienteId);
    }

    @SuppressWarnings("unused")
    private ClienteDTO obtenerFallback(Long clienteId, Throwable t) {
        if (t instanceof HttpClientErrorException e) {
            throw e;
        }
        throw new ServicioNoDisponibleException(
                "No es posible validar al cliente en este momento (ms-clientes no disponible). Intente nuevamente en unos segundos.", t);
    }
}
