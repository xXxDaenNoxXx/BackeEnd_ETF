package cl.banco.clientes.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.banco.clientes.dto.ClienteRequest;
import cl.banco.clientes.dto.ClienteResponse;
import cl.banco.clientes.model.AlertaSeguridad;
import cl.banco.clientes.service.ClienteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService service;

    @PostMapping
    public ResponseEntity<ClienteResponse> crear(@Valid @RequestBody ClienteRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(req));
    }

    @GetMapping
    public List<ClienteResponse> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public ClienteResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @GetMapping("/rut/{rut}")
    public ClienteResponse obtenerPorRut(@PathVariable String rut) {
        return service.obtenerPorRut(rut);
    }

    @PutMapping("/{id}")
    public ClienteResponse actualizar(@PathVariable Long id, @Valid @RequestBody ClienteRequest req) {
        return service.actualizar(id, req);
    }

    @PatchMapping("/{id}/desactivar")
    public ClienteResponse desactivar(@PathVariable Long id) {
        return service.desactivar(id);
    }

    @GetMapping("/{id}/alertas")
    public List<AlertaSeguridad> alertas(@PathVariable Long id) {
        return service.alertas(id);
    }
}
