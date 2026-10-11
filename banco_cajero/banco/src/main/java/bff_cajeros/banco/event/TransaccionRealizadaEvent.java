package bff_cajeros.banco.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransaccionRealizadaEvent(
        Long cuentaId,
        String tipo,
        BigDecimal monto,
        BigDecimal saldoResultante,
        LocalDateTime fecha
) {}