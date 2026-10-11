package ms_core_banco.banco.loader;


import ms_core_banco.banco.model.CuentaInteres;
import ms_core_banco.banco.model.MovimientoAnual;
import ms_core_banco.banco.repository.CuentaInteresRepository;
import ms_core_banco.banco.repository.MovimientoAnualRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatosCoreLoader implements CommandLineRunner {

    private final CuentaInteresRepository cuentaInteresRepository;
    private final MovimientoAnualRepository movimientoAnualRepository;

    private static final Set<String> TIPOS_CUENTA_VALIDOS = Set.of("ahorro", "prestamo", "hipoteca");
    private static final Set<String> TRANSACCIONES_VALIDAS = Set.of("deposito", "retiro", "compra", "pago");

    // Probamos los 4 formatos que existen en el CSV real
    private static final List<DateTimeFormatter> FORMATOS_FECHA = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
    );

    @Override
    public void run(String... args) throws Exception {
        cargarCuentas();
        cargarMovimientos();
    }

    private void cargarCuentas() throws Exception {
        int leidas = 0, cargadas = 0;
        Set<Long> idsVistos = new HashSet<>();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new ClassPathResource("data/intereses.csv").getInputStream(), StandardCharsets.UTF_8))) {
            String linea = br.readLine(); // salta encabezado
            while ((linea = br.readLine()) != null) {
                leidas++;
                CuentaInteres cuenta = parsearCuenta(linea, idsVistos);
                if (cuenta != null) {
                    cuentaInteresRepository.save(cuenta);
                    cargadas++;
                }
            }
        }
        log.info("Cuentas: {} leídas, {} cargadas, {} descartadas", leidas, cargadas, leidas - cargadas);
    }

    private CuentaInteres parsearCuenta(String linea, Set<Long> idsVistos) {
        String[] c = linea.split(",", -1);
        if (c.length < 5) return null;

        try {
            Long cuentaId = Long.parseLong(c[0].trim());
            if (!idsVistos.add(cuentaId)) return null; // cuenta_id duplicado -> se descarta

            String nombre = c[1].trim();
            if (nombre.isBlank()) return null;

            if (c[2].isBlank()) return null;
            BigDecimal saldo = new BigDecimal(c[2].trim());

            if (c[3].isBlank()) return null;
            Integer edad = Integer.parseInt(c[3].trim());

            String tipo = c[4].trim().toLowerCase();
            if (!TIPOS_CUENTA_VALIDOS.contains(tipo)) return null;

            return new CuentaInteres(cuentaId, nombre, saldo, edad, tipo);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void cargarMovimientos() throws Exception {
        int leidas = 0, cargadas = 0;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new ClassPathResource("data/cuentas_anuales.csv").getInputStream(), StandardCharsets.UTF_8))) {
            String linea = br.readLine();
            while ((linea = br.readLine()) != null) {
                leidas++;
                MovimientoAnual mov = parsearMovimiento(linea);
                if (mov != null) {
                    movimientoAnualRepository.save(mov);
                    cargadas++;
                }
            }
        }
        log.info("Movimientos: {} leídos, {} cargados, {} descartados", leidas, cargadas, leidas - cargadas);
    }

    private MovimientoAnual parsearMovimiento(String linea) {
        String[] c = linea.split(",", -1);
        if (c.length < 5) return null;

        try {
            Long cuentaId = Long.parseLong(c[0].trim());

            LocalDate fecha = parsearFecha(c[1].trim());
            if (fecha == null) return null;

            String transaccion = normalizarTransaccion(c[2].trim());
            if (!TRANSACCIONES_VALIDAS.contains(transaccion)) return null;

            if (c[3].isBlank()) return null;
            BigDecimal monto = new BigDecimal(c[3].trim());

            String descripcion = c.length > 4 ? c[4].trim() : "";

            return new MovimientoAnual(null, cuentaId, fecha, transaccion, monto, descripcion);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parsearFecha(String texto) {
        if (texto.isBlank()) return null;
        for (DateTimeFormatter formato : FORMATOS_FECHA) {
            try {
                return LocalDate.parse(texto, formato);
            } catch (Exception ignored) {
                // probamos el siguiente formato
            }
        }
        return null;
    }

    private String normalizarTransaccion(String texto) {
        return texto.toLowerCase()
                .replace("ó", "o")
                .replace("é", "e")
                .replace("á", "a")
                .replace("í", "i")
                .replace("ú", "u");
    }
}