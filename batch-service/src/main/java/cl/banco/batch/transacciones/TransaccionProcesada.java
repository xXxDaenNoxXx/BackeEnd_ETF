package cl.banco.batch.transacciones;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransaccionProcesada(long id, LocalDate fecha, BigDecimal monto, String tipo,
                                   String estado, String motivoAnomalia) {
}
