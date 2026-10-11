package cl.banco.cuentas.event;

import java.time.LocalDateTime;

/** tipo: CUENTA_ABIERTA | CUENTA_ACTUALIZADA | CUENTA_CERRADA */
public record CuentaEvent(String tipo, Long cuentaId, Long clienteId, String estado, LocalDateTime fecha) {
}
