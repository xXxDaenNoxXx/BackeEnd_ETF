package bff_cajeros.banco.client;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import bff_cajeros.banco.dto.core.CuentaCoreDTO;
import bff_cajeros.banco.dto.core.RetiroRequestCoreDTO;
import bff_cajeros.banco.exception.ServicioNoDisponibleException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MsCoreClient {

    private final RestClient coreRestClient;

    public CuentaCoreDTO obtenerCuenta(Long id) {
        return coreRestClient.get()
                .uri("/core/cuentas/{id}", id)
                .retrieve()
                .body(CuentaCoreDTO.class);
    }

    // Orden real de Resilience4j: Retry envuelve al CircuitBreaker.
    // Por eso el fallback va en @Retry (el mas externo): se ejecuta solo cuando
    // ya se agotaron los reintentos o cuando el circuito esta abierto.
    @Retry(name = "msCoreBanco", fallbackMethod = "retirarFallback")
    @CircuitBreaker(name = "msCoreBanco")
    public CuentaCoreDTO retirar(Long id, BigDecimal monto) {
        return coreRestClient.post()
                .uri("/core/cuentas/{id}/retiro", id)
                .body(new RetiroRequestCoreDTO(monto))
                .retrieve()
                .body(CuentaCoreDTO.class);
    }

    // Mismos parametros que retirar() + Throwable al final.
    @SuppressWarnings("unused")
    private CuentaCoreDTO retirarFallback(Long id, BigDecimal monto, Throwable t) {
        // Los 4xx (saldo insuficiente, cuenta inexistente) son errores de NEGOCIO, no una caida:
        // se relanzan tal cual para que BffCajeroService los maneje como antes.
        if (t instanceof HttpClientErrorException e) {
            throw e;
        }
        // Cualquier otra cosa (ms-core-banco caido, timeout, circuito abierto) = servicio no disponible.
        throw new ServicioNoDisponibleException(
                "ms-core-banco no esta disponible en este momento. Intente nuevamente en unos minutos.", t);
    }
}