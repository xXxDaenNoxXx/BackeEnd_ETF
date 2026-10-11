package cl.banco.pagos.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.banco.pagos.dto.DepositoRequest;
import cl.banco.pagos.dto.PagoRequest;
import cl.banco.pagos.dto.TransferenciaRequest;
import cl.banco.pagos.model.Pago;
import cl.banco.pagos.service.PagoService;
import cl.banco.pagos.service.PagoService.Resultado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Cabecera opcional "Idempotency-Key": repetir la misma peticion con la misma clave devuelve el resultado
 * original sin volver a mover dinero (seguro ante reintentos del cliente, de la red o del gateway).
 */
@RestController
@RequestMapping("/pagos")
@RequiredArgsConstructor
public class PagoController {

    private final PagoService service;

    @PostMapping("/deposito")
    public ResponseEntity<Pago> deposito(@RequestHeader(value = "Idempotency-Key", required = false) String clave,
                                         @Valid @RequestBody DepositoRequest req) {
        return responder(service.deposito(req, PagoService.claveOGenerada(clave)));
    }

    @PostMapping("/pago")
    public ResponseEntity<Pago> pago(@RequestHeader(value = "Idempotency-Key", required = false) String clave,
                                     @Valid @RequestBody PagoRequest req) {
        return responder(service.pago(req, PagoService.claveOGenerada(clave)));
    }

    @PostMapping("/transferencia")
    public ResponseEntity<Pago> transferencia(@RequestHeader(value = "Idempotency-Key", required = false) String clave,
                                              @Valid @RequestBody TransferenciaRequest req) {
        return responder(service.transferencia(req, PagoService.claveOGenerada(clave)));
    }

    @GetMapping("/{id}")
    public Pago obtener(@PathVariable String id) {
        return service.obtener(id);
    }

    @GetMapping("/cuenta/{cuentaId}")
    public List<Pago> porCuenta(@PathVariable Long cuentaId) {
        return service.porCuenta(cuentaId);
    }

    private ResponseEntity<Pago> responder(Resultado r) {
        return ResponseEntity.status(r.status()).body(r.pago());
    }
}
