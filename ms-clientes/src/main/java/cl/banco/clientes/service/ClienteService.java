package cl.banco.clientes.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.banco.clientes.dto.ClienteRequest;
import cl.banco.clientes.dto.ClienteResponse;
import cl.banco.clientes.event.ClienteEvent;
import cl.banco.clientes.event.EventPublisher;
import cl.banco.clientes.exception.RecursoNoEncontradoException;
import cl.banco.clientes.exception.ReglaNegocioException;
import cl.banco.clientes.model.AlertaSeguridad;
import cl.banco.clientes.model.Cliente;
import cl.banco.clientes.repository.AlertaRepository;
import cl.banco.clientes.repository.ClienteRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final AlertaRepository alertaRepository;
    private final EventPublisher eventPublisher;

    @Value("${clientes.topic-eventos}")
    private String topicoEventos;

    @Transactional
    public ClienteResponse crear(ClienteRequest req) {
        String rut = req.rut().toUpperCase();
        if (clienteRepository.existsByRut(rut)) {
            throw new ReglaNegocioException("Ya existe un cliente con RUT " + rut);
        }
        Cliente c = new Cliente();
        c.setRut(rut);
        aplicar(c, req);
        c.setEstado("ACTIVO");
        c.setCreadoEn(LocalDateTime.now());
        Cliente guardado = clienteRepository.save(c);
        publicar("CLIENTE_CREADO", guardado);
        return ClienteResponse.de(guardado);
    }

    @Transactional(readOnly = true)
    public ClienteResponse obtener(Long id) {
        return ClienteResponse.de(buscar(id));
    }

    @Transactional(readOnly = true)
    public ClienteResponse obtenerPorRut(String rut) {
        return clienteRepository.findByRut(rut.toUpperCase()).map(ClienteResponse::de)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un cliente con RUT " + rut));
    }

    @Transactional(readOnly = true)
    public List<ClienteResponse> listar() {
        return clienteRepository.findAll().stream().map(ClienteResponse::de).toList();
    }

    @Transactional
    public ClienteResponse actualizar(Long id, ClienteRequest req) {
        Cliente c = buscar(id);
        if (!c.getRut().equalsIgnoreCase(req.rut())) {
            throw new ReglaNegocioException("El RUT de un cliente no se puede modificar");
        }
        aplicar(c, req);
        Cliente guardado = clienteRepository.save(c);
        publicar("CLIENTE_ACTUALIZADO", guardado);
        return ClienteResponse.de(guardado);
    }

    @Transactional
    public ClienteResponse desactivar(Long id) {
        Cliente c = buscar(id);
        c.setEstado("INACTIVO");
        Cliente guardado = clienteRepository.save(c);
        publicar("CLIENTE_DESACTIVADO", guardado);
        return ClienteResponse.de(guardado);
    }

    @Transactional(readOnly = true)
    public List<AlertaSeguridad> alertas(Long id) {
        buscar(id);
        return alertaRepository.findByClienteIdOrderByFechaDesc(id);
    }

    private Cliente buscar(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente " + id));
    }

    private void aplicar(Cliente c, ClienteRequest req) {
        c.setNombre(req.nombre());
        c.setEmail(req.email());
        c.setTelefono(req.telefono());
        c.setPerfil(req.perfil() == null ? "BASICO" : req.perfil());
    }

    private void publicar(String tipo, Cliente c) {
        eventPublisher.publicar(topicoEventos, String.valueOf(c.getId()),
                new ClienteEvent(tipo, c.getId(), c.getRut(), c.getPerfil(), LocalDateTime.now()));
    }
}
