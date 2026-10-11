package cl.banco.pagos.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** Termina las sagas que quedaron a medias (reverso pendiente) apenas ms-cuentas vuelve a responder. */
@Component
@RequiredArgsConstructor
public class CompensacionScheduler {

    private final PagoService pagoService;

    @Scheduled(initialDelay = 30000, fixedDelay = 30000)
    public void reintentar() {
        pagoService.reintentarCompensacionesPendientes();
    }
}
