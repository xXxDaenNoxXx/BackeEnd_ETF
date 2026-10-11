package ms_core_banco.banco.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import ms_core_banco.banco.dto.ErrorResponseDTO;
import ms_core_banco.banco.dto.RetiroRequestDTO;
import ms_core_banco.banco.event.TransaccionEventPublisher;
import ms_core_banco.banco.event.TransaccionRealizadaEvent;
import ms_core_banco.banco.model.CuentaInteres;
import ms_core_banco.banco.model.MovimientoAnual;
import ms_core_banco.banco.repository.CuentaInteresRepository;
import ms_core_banco.banco.repository.MovimientoAnualRepository;

@RestController
@RequestMapping("/core/cuentas")
@RequiredArgsConstructor
public class CuentaCoreController {

    private final CuentaInteresRepository cuentaInteresRepository;
    private final MovimientoAnualRepository movimientoAnualRepository;

    @GetMapping
    public List<CuentaInteres> listar() {
        return cuentaInteresRepository.findAll();
    }

    @GetMapping("/{id}")
    public CuentaInteres obtener(@PathVariable Long id) {
        return cuentaInteresRepository.findById(id).orElse(null);
    }

    @GetMapping("/{id}/movimientos")
    public List<MovimientoAnual> movimientos(@PathVariable Long id) {
        return movimientoAnualRepository.findByCuentaId(id);
    }

private final TransaccionEventPublisher eventPublisher; // <-- nuevo

// ... (métodos listar, obtener, movimientos sin cambios)

@PostMapping("/{id}/retiro")
public ResponseEntity<?> retirar(@PathVariable Long id, @RequestBody RetiroRequestDTO request) {
    return cuentaInteresRepository.findById(id)
            .map(cuenta -> {
                if (cuenta.getSaldo().compareTo(request.monto()) < 0) {
                    return ResponseEntity.badRequest()
                            .body(new ErrorResponseDTO("Saldo insuficiente para realizar el retiro"));
                }

                cuenta.setSaldo(cuenta.getSaldo().subtract(request.monto()));
                cuentaInteresRepository.save(cuenta);

                MovimientoAnual movimiento = new MovimientoAnual(
                        null, id, LocalDate.now(), "retiro", request.monto(), "Retiro en cajero automático"
                );
                movimientoAnualRepository.save(movimiento);

                // Publicamos el evento DESPUES de guardar todo: la transaccion ya es un hecho,
                // ahora avisamos al resto del sistema de forma asincrona.
                eventPublisher.publicar(new TransaccionRealizadaEvent(
                        id, "retiro", request.monto(), cuenta.getSaldo(), LocalDateTime.now()
                ));

                return ResponseEntity.ok(cuenta);
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
}
}