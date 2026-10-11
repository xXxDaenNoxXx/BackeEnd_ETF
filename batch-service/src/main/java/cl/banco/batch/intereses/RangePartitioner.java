package cl.banco.batch.intereses;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Divide el rango de cuenta_id en N particiones; cada una la procesa un hilo (step "worker") en paralelo.
 * Es la base de la escalabilidad: el mismo diseno permite pasar a particionado REMOTO (workers en otros nodos).
 */
public class RangePartitioner implements Partitioner {

    private final JdbcTemplate jdbc;

    public RangePartitioner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        Long min = jdbc.queryForObject("SELECT COALESCE(MIN(cuenta_id), 0) FROM cuenta_staging", Long.class);
        Long max = jdbc.queryForObject("SELECT COALESCE(MAX(cuenta_id), 0) FROM cuenta_staging", Long.class);
        long desde = min == null ? 0 : min;
        long hasta = max == null ? 0 : max;

        long tamano = (hasta - desde) / gridSize + 1;
        Map<String, ExecutionContext> particiones = new LinkedHashMap<>();
        long inicio = desde;
        int numero = 0;
        while (inicio <= hasta) {
            ExecutionContext ctx = new ExecutionContext();
            ctx.putLong("minValue", inicio);
            ctx.putLong("maxValue", Math.min(inicio + tamano - 1, hasta));
            particiones.put("particion" + numero, ctx);
            inicio += tamano;
            numero++;
        }
        return particiones;
    }
}
