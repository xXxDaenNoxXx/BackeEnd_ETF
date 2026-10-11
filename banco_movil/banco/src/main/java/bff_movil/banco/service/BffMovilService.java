package bff_movil.banco.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import bff_movil.banco.client.MsCoreClient;
import bff_movil.banco.dto.ResumenMovilDTO;
import bff_movil.banco.dto.SaldoMovilDTO;
import bff_movil.banco.dto.core.CuentaCoreDTO;
import bff_movil.banco.dto.core.MovimientoCoreDTO;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BffMovilService {

    private static final int MAX_MOVIMIENTOS = 5;

    private final MsCoreClient core;

    public SaldoMovilDTO saldo(Long id) {
        CuentaCoreDTO c = buscar(id);
        return c == null ? null : new SaldoMovilDTO(c.cuentaId(), c.saldo());
    }

    public ResumenMovilDTO resumen(Long id) {
        CuentaCoreDTO c = buscar(id);
        if (c == null) {
            return null;
        }
        List<ResumenMovilDTO.Mov> ultimos = core.movimientos(id).stream()
                .sorted(Comparator.comparing(MovimientoCoreDTO::fecha).reversed())
                .limit(MAX_MOVIMIENTOS)
                .map(m -> new ResumenMovilDTO.Mov(m.fecha().toString(), m.transaccion(), m.monto()))
                .toList();
        return new ResumenMovilDTO(c.cuentaId(), c.saldo(), ultimos);
    }

    private CuentaCoreDTO buscar(Long id) {
        try {
            return core.obtenerCuenta(id);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }
}
