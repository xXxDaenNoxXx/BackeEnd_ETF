package cl.banco.clientes.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Mismo contrato que publica ms-pagos en el topico alertas.seguridad. */
public record AlertaSeguridadEvent(String tipo, Long clienteId, Long cuentaId, BigDecimal monto, String pagoId,
                                   String mensaje, LocalDateTime fecha) {
}
