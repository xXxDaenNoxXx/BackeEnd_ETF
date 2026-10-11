package cl.banco.batch.estados;

import org.springframework.batch.item.ItemProcessor;

/** saldo_neto = depositos - egresos del anio. Si es negativo el estado queda REVISAR (lo mira auditoria). Stateless. */
public class EstadoProcessor implements ItemProcessor<AgregadoMovimientos, EstadoCuentaAnual> {

    @Override
    public EstadoCuentaAnual process(AgregadoMovimientos a) {
        var neto = a.depositos().subtract(a.egresos());
        return new EstadoCuentaAnual(a.cuentaId(), a.anio(), a.cantidad(), a.depositos(), a.egresos(), neto,
                neto.signum() < 0 ? "REVISAR" : "OK");
    }
}
