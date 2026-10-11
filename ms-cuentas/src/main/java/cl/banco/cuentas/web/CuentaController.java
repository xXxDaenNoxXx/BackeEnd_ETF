package cl.banco.cuentas.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cl.banco.cuentas.dto.AbrirCuentaRequest;
import cl.banco.cuentas.dto.CuentaResponse;
import cl.banco.cuentas.dto.MantenerCuentaRequest;
import cl.banco.cuentas.dto.MovimientoRequest;
import cl.banco.cuentas.model.ActividadCuenta;
import cl.banco.cuentas.model.MovimientoCuenta;
import cl.banco.cuentas.service.CuentaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/cuentas")
@RequiredArgsConstructor
public class CuentaController {

    private final CuentaService service;

    @PostMapping
    public ResponseEntity<CuentaResponse> abrir(@Valid @RequestBody AbrirCuentaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.abrir(req));
    }

    @GetMapping("/{id}")
    public CuentaResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @GetMapping
    public List<CuentaResponse> porCliente(@RequestParam Long clienteId) {
        return service.listarPorCliente(clienteId);
    }

    @PutMapping("/{id}")
    public CuentaResponse mantener(@PathVariable Long id, @Valid @RequestBody MantenerCuentaRequest req) {
        return service.mantener(id, req);
    }

    @PostMapping("/{id}/cierre")
    public CuentaResponse cerrar(@PathVariable Long id) {
        return service.cerrar(id);
    }

    @PostMapping("/{id}/debitar")
    public CuentaResponse debitar(@PathVariable Long id, @Valid @RequestBody MovimientoRequest req) {
        return service.debitar(id, req.monto(), req.referencia());
    }

    @PostMapping("/{id}/acreditar")
    public CuentaResponse acreditar(@PathVariable Long id, @Valid @RequestBody MovimientoRequest req) {
        return service.acreditar(id, req.monto(), req.referencia());
    }

    @GetMapping("/{id}/movimientos")
    public List<MovimientoCuenta> movimientos(@PathVariable Long id) {
        return service.movimientos(id);
    }

    @GetMapping("/{id}/actividad")
    public ActividadCuenta actividad(@PathVariable Long id) {
        return service.actividad(id);
    }
}
