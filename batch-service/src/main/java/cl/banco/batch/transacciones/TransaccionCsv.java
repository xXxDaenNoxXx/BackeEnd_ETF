package cl.banco.batch.transacciones;

/** Linea cruda del CSV legacy (todo texto: la validacion ocurre en el processor, no en el parseo). */
public record TransaccionCsv(String id, String fecha, String monto, String tipo) {
}
