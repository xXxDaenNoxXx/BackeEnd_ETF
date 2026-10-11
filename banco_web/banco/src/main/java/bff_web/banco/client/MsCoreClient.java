package bff_web.banco.client;

import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import bff_web.banco.dto.core.CuentaCoreDTO;
import bff_web.banco.dto.core.MovimientoCoreDTO;
import bff_web.banco.exception.ServicioNoDisponibleException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;

/**
 * Cliente de ms-core-banco (solo lectura) con Retry + CircuitBreaker.
 * Fallback: los 4xx (negocio) se propagan; cualquier otra falla => ServicioNoDisponibleException (HTTP 503).
 */
@Component
@RequiredArgsConstructor
public class MsCoreClient {

    private final RestClient coreRestClient;

    @Retry(name = "msCoreBanco", fallbackMethod = "obtenerFallback")
    @CircuitBreaker(name = "msCoreBanco")
    public CuentaCoreDTO obtenerCuenta(Long id) {
        return coreRestClient.get()
                .uri("/core/cuentas/{id}", id)
                .retrieve()
                .body(CuentaCoreDTO.class);
    }

    @Retry(name = "msCoreBanco", fallbackMethod = "movimientosFallback")
    @CircuitBreaker(name = "msCoreBanco")
    public List<MovimientoCoreDTO> movimientos(Long id) {
        return coreRestClient.get()
                .uri("/core/cuentas/{id}/movimientos", id)
                .retrieve()
                .body(new ParameterizedTypeReference<List<MovimientoCoreDTO>>() { });
    }

    @Retry(name = "msCoreBanco", fallbackMethod = "listarFallback")
    @CircuitBreaker(name = "msCoreBanco")
    public List<CuentaCoreDTO> listarCuentas() {
        return coreRestClient.get()
                .uri("/core/cuentas")
                .retrieve()
                .body(new ParameterizedTypeReference<List<CuentaCoreDTO>>() { });
    }

    @SuppressWarnings("unused")
    private CuentaCoreDTO obtenerFallback(Long id, Throwable t) {
        throw traducir(t);
    }

    @SuppressWarnings("unused")
    private List<MovimientoCoreDTO> movimientosFallback(Long id, Throwable t) {
        throw traducir(t);
    }

    @SuppressWarnings("unused")
    private List<CuentaCoreDTO> listarFallback(Throwable t) {
        throw traducir(t);
    }

    private RuntimeException traducir(Throwable t) {
        if (t instanceof HttpClientErrorException e) {
            return e;
        }
        return new ServicioNoDisponibleException(
                "ms-core-banco no esta disponible en este momento. Intente nuevamente en unos minutos.", t);
    }
}
