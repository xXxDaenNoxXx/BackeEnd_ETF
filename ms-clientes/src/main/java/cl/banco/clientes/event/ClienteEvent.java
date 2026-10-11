package cl.banco.clientes.event;

import java.time.LocalDateTime;

/** tipo: CLIENTE_CREADO | CLIENTE_ACTUALIZADO | CLIENTE_DESACTIVADO */
public record ClienteEvent(String tipo, Long clienteId, String rut, String perfil, LocalDateTime fecha) {
}
