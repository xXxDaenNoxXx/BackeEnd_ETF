package cl.banco.pagos.client;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import cl.banco.pagos.dto.CuentaDTO;
import cl.banco.pagos.dto.MovimientoRequestDTO;
import cl.banco.pagos.exception.ServicioNoDisponibleException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;

/**
 * Llamadas resilientes a ms-cuentas. Es SEGURO reintentar debitar/acreditar porque ms-cuentas
 * es idempotente por 'referencia'. Fallback: ms-cuentas caido => ServicioNoDisponibleException
 * (el pago se registra como RECHAZADO con el motivo, sin tocar saldos). Los 4xx se propagan: son errores de negocio.
 */
@Component
@RequiredArgsConstructor
public class CuentaClient {

    private final RestTemplate restTemplate;

    @Retry(name = "msCuentas", fallbackMethod = "debitarFallback")
    @CircuitBreaker(name = "msCuentas")
    public CuentaDTO debitar(Long cuentaId, BigDecimal monto, String referencia) {
        return restTemplate.postForObject("http://ms-cuentas/cuentas/{id}/debitar",
                new MovimientoRequestDTO(monto, referencia), CuentaDTO.class, cuentaId);
    }

    @Retry(name = "msCuentas", fallbackMethod = "acreditarFallback")
    @CircuitBreaker(name = "msCuentas")
    public CuentaDTO acreditar(Long cuentaId, BigDecimal monto, String referencia) {
        return restTemplate.postForObject("http://ms-cuentas/cuentas/{id}/acreditar",
                new MovimientoRequestDTO(monto, referencia), CuentaDTO.class, cuentaId);
    }

    @SuppressWarnings("unused")
    private CuentaDTO debitarFallback(Long cuentaId, BigDecimal monto, String referencia, Throwable t) {
        return fallback(t);
    }

    @SuppressWarnings("unused")
    private CuentaDTO acreditarFallback(Long cuentaId, BigDecimal monto, String referencia, Throwable t) {
        return fallback(t);
    }

    private CuentaDTO fallback(Throwable t) {
        if (t instanceof HttpClientErrorException e) {
            throw e;
        }
        throw new ServicioNoDisponibleException(
                "ms-cuentas no esta disponible en este momento. La operacion no se realizo; reintente en unos segundos.", t);
    }
}
