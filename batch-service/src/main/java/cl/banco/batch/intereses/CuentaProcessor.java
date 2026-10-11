package cl.banco.batch.intereses;

import java.math.BigDecimal;
import java.util.Set;

import org.springframework.batch.item.ItemProcessor;

import cl.banco.batch.common.RegistroInvalidoException;

/**
 * Reglas de calidad sobre la cuenta (todas lanzan RegistroInvalidoException => skip + traza del motivo):
 *  - cuenta_id numerico, nombre informado ("Unknown" se acepta: es un nombre real en el legacy)
 *  - saldo informado, numerico y no negativo
 *  - edad informada y dentro de un rango razonable (18..120)
 *  - tipo de cuenta conocido (ahorro | prestamo | hipoteca)
 * La deduplicacion por cuenta_id ("primer registro valido gana") la resuelve la base de datos
 * con ON CONFLICT DO NOTHING, asi tambien es correcta tras una reejecucion.
 */
public class CuentaProcessor implements ItemProcessor<CuentaCsv, CuentaStaging> {

    private static final Set<String> TIPOS = Set.of("ahorro", "prestamo", "hipoteca");

    @Override
    public CuentaStaging process(CuentaCsv c) {
        long id;
        try {
            id = Long.parseLong(c.cuentaId().trim());
        } catch (NumberFormatException e) {
            throw new RegistroInvalidoException("cuenta_id no numerico: '" + c.cuentaId() + "'");
        }

        if (c.nombre() == null || c.nombre().isBlank()) {
            throw new RegistroInvalidoException("nombre faltante (cuenta " + id + ")");
        }

        if (c.saldo() == null || c.saldo().isBlank()) {
            throw new RegistroInvalidoException("saldo faltante (cuenta " + id + ")");
        }
        BigDecimal saldo;
        try {
            saldo = new BigDecimal(c.saldo().trim());
        } catch (NumberFormatException e) {
            throw new RegistroInvalidoException("saldo no numerico: '" + c.saldo() + "' (cuenta " + id + ")");
        }
        if (saldo.signum() < 0) {
            throw new RegistroInvalidoException("saldo negativo (cuenta " + id + ")");
        }

        if (c.edad() == null || c.edad().isBlank()) {
            throw new RegistroInvalidoException("edad faltante (cuenta " + id + ")");
        }
        int edad;
        try {
            edad = Integer.parseInt(c.edad().trim());
        } catch (NumberFormatException e) {
            throw new RegistroInvalidoException("edad no numerica: '" + c.edad() + "' (cuenta " + id + ")");
        }
        if (edad < 18 || edad > 120) {
            throw new RegistroInvalidoException("edad fuera de rango (18-120): " + edad + " (cuenta " + id + ")");
        }

        String tipo = c.tipo() == null ? "" : c.tipo().trim().toLowerCase();
        if (!TIPOS.contains(tipo)) {
            throw new RegistroInvalidoException("tipo de cuenta desconocido: '" + tipo + "' (cuenta " + id + ")");
        }

        return new CuentaStaging(id, c.nombre().trim(), saldo, edad, tipo);
    }
}
