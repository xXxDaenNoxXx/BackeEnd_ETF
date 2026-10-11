package cl.banco.clientes.dto;

import java.time.LocalDateTime;

import cl.banco.clientes.model.Cliente;

public record ClienteResponse(Long id, String rut, String nombre, String email, String telefono, String perfil,
                              String estado, LocalDateTime creadoEn) {

    public static ClienteResponse de(Cliente c) {
        return new ClienteResponse(c.getId(), c.getRut(), c.getNombre(), c.getEmail(), c.getTelefono(),
                c.getPerfil(), c.getEstado(), c.getCreadoEn());
    }
}
