package bff_cajeros.banco.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import bff_cajeros.banco.client.MsCoreClient;
import bff_cajeros.banco.dto.CuentaCajeroSaldoDTO;
import bff_cajeros.banco.dto.RetiroResponseDTO;
import bff_cajeros.banco.dto.core.CuentaCoreDTO;
import bff_cajeros.banco.exception.ServicioNoDisponibleException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BffCajeroService {

    private final MsCoreClient msCoreClient;

    public CuentaCajeroSaldoDTO consultarSaldo(Long id) {
        try {
            CuentaCoreDTO cuenta = msCoreClient.obtenerCuenta(id);
            if (cuenta == null)
                return null;
            return new CuentaCajeroSaldoDTO(cuenta.cuentaId(), cuenta.saldo());
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    public RetiroResponseDTO retirar(Long id, BigDecimal monto) {
        try {
            CuentaCoreDTO actualizada = msCoreClient.retirar(id, monto);
            return new RetiroResponseDTO(actualizada.cuentaId(), actualizada.saldo(), "Retiro exitoso");
        } catch (HttpClientErrorException.NotFound e) {
            return null; // la cuenta no existe
        } catch (HttpClientErrorException.BadRequest e) {
            return new RetiroResponseDTO(id, null, "Retiro rechazado: saldo insuficiente");
        } catch (ServicioNoDisponibleException e) {
            // Aquí es donde entra la tolerancia a fallos: el circuito está abierto
            // o se agotaron los reintentos. Respondemos con un mensaje claro en vez de
            // caernos.
            return new RetiroResponseDTO(id, null, e.getMessage());
        }
    }
}