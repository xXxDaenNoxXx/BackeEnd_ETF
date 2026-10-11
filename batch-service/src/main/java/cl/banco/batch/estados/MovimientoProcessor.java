package cl.banco.batch.estados;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import org.springframework.batch.item.ItemProcessor;

import cl.banco.batch.common.FechaUtil;
import cl.banco.batch.common.RegistroInvalidoException;

/**
 * Reglas de calidad (rechazo => skip + traza):
 *  - cuenta_id numerico; fecha en alguno de los 4 formatos legacy
 *  - transaccion conocida (deposito | retiro | compra | pago); "depósito" con tilde se normaliza a "deposito"
 *  - monto informado, numerico y distinto de cero. El signo del CSV legacy no es confiable
 *    (aparecen retiros positivos y depositos negativos), por eso se guarda el VALOR ABSOLUTO y el
 *    sentido (ingreso/egreso) lo determina el tipo de transaccion.
 * Una descripcion vacia NO es motivo de rechazo: se reemplaza por "Sin descripcion".
 */
public class MovimientoProcessor implements ItemProcessor<MovimientoCsv, MovimientoAnual> {

    private static final Set<String> TRANSACCIONES = Set.of("deposito", "retiro", "compra", "pago");

    @Override
    public MovimientoAnual process(MovimientoCsv m) {
        long cuentaId;
        try {
            cuentaId = Long.parseLong(m.cuentaId().trim());
        } catch (NumberFormatException e) {
            throw new RegistroInvalidoException("cuenta_id no numerico: '" + m.cuentaId() + "'");
        }

        LocalDate fecha = FechaUtil.parsear(m.fecha());
        if (fecha == null) {
            throw new RegistroInvalidoException("fecha invalida: '" + m.fecha() + "' (cuenta " + cuentaId + ")");
        }

        String transaccion = normalizar(m.transaccion());
        if (!TRANSACCIONES.contains(transaccion)) {
            throw new RegistroInvalidoException("transaccion desconocida: '" + m.transaccion() + "' (cuenta " + cuentaId + ")");
        }

        if (m.monto() == null || m.monto().isBlank()) {
            throw new RegistroInvalidoException("monto faltante (cuenta " + cuentaId + ")");
        }
        BigDecimal monto;
        try {
            monto = new BigDecimal(m.monto().trim());
        } catch (NumberFormatException e) {
            throw new RegistroInvalidoException("monto no numerico: '" + m.monto() + "' (cuenta " + cuentaId + ")");
        }
        if (monto.signum() == 0) {
            throw new RegistroInvalidoException("monto cero, movimiento sin efecto (cuenta " + cuentaId + ")");
        }

        String descripcion = (m.descripcion() == null || m.descripcion().isBlank()) ? "Sin descripcion" : m.descripcion().trim();
        return new MovimientoAnual(cuentaId, fecha, transaccion, monto.abs(), descripcion);
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.trim().toLowerCase()
                .replace("ó", "o").replace("é", "e").replace("á", "a").replace("í", "i").replace("ú", "u");
    }
}
