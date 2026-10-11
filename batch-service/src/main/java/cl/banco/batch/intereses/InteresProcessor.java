package cl.banco.batch.intereses;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import org.springframework.batch.item.ItemProcessor;

/**
 * Interes mensual = saldo x tasa mensual del tipo de cuenta (tasas en Config Server).
 *  - ahorro  : interes a favor del cliente  -> se abona al saldo
 *  - prestamo / hipoteca: interes cobrado   -> se suma a la deuda
 * En ambos casos nuevo_saldo = saldo + interes. Redondeo HALF_UP a 2 decimales (igual que el legacy en COBOL).
 */
public class InteresProcessor implements ItemProcessor<CuentaStaging, InteresMensual> {

    private final Map<String, BigDecimal> tasas;
    private final String periodo;

    public InteresProcessor(Map<String, BigDecimal> tasas, String periodo) {
        this.tasas = tasas;
        this.periodo = periodo;
    }

    @Override
    public InteresMensual process(CuentaStaging c) {
        BigDecimal tasa = tasas.get(c.tipo());
        if (tasa == null) {
            throw new IllegalStateException("No hay tasa configurada para el tipo de cuenta '" + c.tipo()
                    + "' (batch.interes." + c.tipo() + " en el Config Server)");
        }
        BigDecimal interes = c.saldo().multiply(tasa).setScale(2, RoundingMode.HALF_UP);
        return new InteresMensual(c.cuentaId(), periodo, c.nombre(), c.tipo(), c.saldo(), tasa,
                interes, c.saldo().add(interes));
    }
}
