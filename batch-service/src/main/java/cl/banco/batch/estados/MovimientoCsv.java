package cl.banco.batch.estados;

/** Linea cruda de estados_financieros_anuales.csv */
public record MovimientoCsv(String cuentaId, String fecha, String transaccion, String monto, String descripcion) {
}
