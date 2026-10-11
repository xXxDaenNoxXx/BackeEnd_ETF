package cl.banco.cuentas.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;

import cl.banco.cuentas.client.ClienteClient;
import cl.banco.cuentas.dto.AbrirCuentaRequest;
import cl.banco.cuentas.dto.ClienteDTO;
import cl.banco.cuentas.dto.CuentaResponse;
import cl.banco.cuentas.dto.MantenerCuentaRequest;
import cl.banco.cuentas.event.CuentaEvent;
import cl.banco.cuentas.event.EventPublisher;
import cl.banco.cuentas.exception.RecursoNoEncontradoException;
import cl.banco.cuentas.exception.ReglaNegocioException;
import cl.banco.cuentas.model.ActividadCuenta;
import cl.banco.cuentas.model.Cuenta;
import cl.banco.cuentas.model.MovimientoCuenta;
import cl.banco.cuentas.repository.ActividadRepository;
import cl.banco.cuentas.repository.CuentaRepository;
import cl.banco.cuentas.repository.MovimientoRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CuentaService {

    private final CuentaRepository cuentaRepository;
    private final MovimientoRepository movimientoRepository;
    private final ActividadRepository actividadRepository;
    private final ClienteClient clienteClient;
    private final EventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    @Value("${cuentas.topic-eventos}")
    private String topicoEventos;

    // ------------------------------------------------------------------ apertura / cierre / mantenimiento
    /** La llamada remota a ms-clientes se hace FUERA de la transaccion de base de datos. */
    public CuentaResponse abrir(AbrirCuentaRequest req) {
        ClienteDTO cliente;
        try {
            cliente = clienteClient.obtener(req.clienteId());
        } catch (HttpClientErrorException.NotFound e) {
            throw new ReglaNegocioException("El cliente " + req.clienteId() + " no existe");
        }
        if (cliente == null || !"ACTIVO".equals(cliente.estado())) {
            throw new ReglaNegocioException("El cliente " + req.clienteId() + " no esta activo");
        }
        return transactionTemplate.execute(status -> guardarNueva(req));
    }

    private CuentaResponse guardarNueva(AbrirCuentaRequest req) {
        Cuenta c = new Cuenta();
        c.setNumero(generarNumero());
        c.setClienteId(req.clienteId());
        c.setTipo(req.tipo());
        c.setAlias(req.alias());
        c.setSaldo(req.saldoInicial() == null ? BigDecimal.ZERO : req.saldoInicial());
        c.setEstado("ACTIVA");
        c.setCreadaEn(LocalDateTime.now());
        Cuenta guardada = cuentaRepository.save(c);
        publicar("CUENTA_ABIERTA", guardada);
        return CuentaResponse.de(guardada);
    }

    @Transactional
    public CuentaResponse cerrar(Long id) {
        Cuenta c = cuentaRepository.buscarParaActualizar(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la cuenta " + id));
        if (!"ACTIVA".equals(c.getEstado())) {
            throw new ReglaNegocioException("La cuenta " + id + " ya esta cerrada");
        }
        if (c.getSaldo().signum() != 0) {
            throw new ReglaNegocioException("Solo se puede cerrar una cuenta con saldo 0 (saldo actual: " + c.getSaldo() + ")");
        }
        c.setEstado("CERRADA");
        c.setCerradaEn(LocalDateTime.now());
        Cuenta guardada = cuentaRepository.save(c);
        publicar("CUENTA_CERRADA", guardada);
        return CuentaResponse.de(guardada);
    }

    @Transactional
    public CuentaResponse mantener(Long id, MantenerCuentaRequest req) {
        Cuenta c = cuentaRepository.buscarParaActualizar(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la cuenta " + id));
        if (!"ACTIVA".equals(c.getEstado())) {
            throw new ReglaNegocioException("No se puede modificar una cuenta cerrada");
        }
        if (req.alias() != null) {
            c.setAlias(req.alias());
        }
        if (req.tipo() != null) {
            c.setTipo(req.tipo());
        }
        Cuenta guardada = cuentaRepository.save(c);
        publicar("CUENTA_ACTUALIZADA", guardada);
        return CuentaResponse.de(guardada);
    }

    // ------------------------------------------------------------------ consultas
    @Transactional(readOnly = true)
    public CuentaResponse obtener(Long id) {
        return cuentaRepository.findById(id).map(CuentaResponse::de)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la cuenta " + id));
    }

    @Transactional(readOnly = true)
    public List<CuentaResponse> listarPorCliente(Long clienteId) {
        return cuentaRepository.findByClienteId(clienteId).stream().map(CuentaResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public List<MovimientoCuenta> movimientos(Long id) {
        obtener(id);
        return movimientoRepository.findByCuentaIdOrderByFechaDesc(id);
    }

    @Transactional(readOnly = true)
    public ActividadCuenta actividad(Long id) {
        obtener(id);
        return actividadRepository.findById(id).orElseGet(() -> {
            ActividadCuenta vacia = new ActividadCuenta();
            vacia.setCuentaId(id);
            return vacia;
        });
    }

    // ------------------------------------------------------------------ movimientos (los usa ms-pagos)
    @Transactional
    public CuentaResponse debitar(Long id, BigDecimal monto, String referencia) {
        return aplicar(id, monto, referencia, "DEBITO");
    }

    @Transactional
    public CuentaResponse acreditar(Long id, BigDecimal monto, String referencia) {
        return aplicar(id, monto, referencia, "CREDITO");
    }

    /**
     * Operacion atomica e IDEMPOTENTE:
     *  1. SELECT ... FOR UPDATE sobre la cuenta (nadie mas la modifica mientras se decide)
     *  2. si ya existe un movimiento con la misma referencia => es un reintento: se devuelve el resultado sin reaplicar
     *  3. valida estado y saldo, actualiza el saldo y registra el movimiento en la misma transaccion
     */
    private CuentaResponse aplicar(Long id, BigDecimal monto, String referencia, String tipo) {
        Cuenta c = cuentaRepository.buscarParaActualizar(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la cuenta " + id));

        if (movimientoRepository.findByCuentaIdAndReferenciaAndTipo(id, referencia, tipo).isPresent()) {
            return CuentaResponse.de(c);
        }
        if (!"ACTIVA".equals(c.getEstado())) {
            throw new ReglaNegocioException("La cuenta " + id + " esta cerrada");
        }
        BigDecimal nuevoSaldo;
        if ("DEBITO".equals(tipo)) {
            if (c.getSaldo().compareTo(monto) < 0) {
                throw new ReglaNegocioException("Saldo insuficiente en la cuenta " + id);
            }
            nuevoSaldo = c.getSaldo().subtract(monto);
        } else {
            nuevoSaldo = c.getSaldo().add(monto);
        }
        c.setSaldo(nuevoSaldo);
        cuentaRepository.save(c);

        MovimientoCuenta m = new MovimientoCuenta();
        m.setCuentaId(id);
        m.setTipo(tipo);
        m.setMonto(monto);
        m.setSaldoResultante(nuevoSaldo);
        m.setReferencia(referencia);
        m.setFecha(LocalDateTime.now());
        movimientoRepository.save(m);
        return CuentaResponse.de(c);
    }

    private void publicar(String tipo, Cuenta c) {
        eventPublisher.publicar(topicoEventos, String.valueOf(c.getId()),
                new CuentaEvent(tipo, c.getId(), c.getClienteId(), c.getEstado(), LocalDateTime.now()));
    }

    private String generarNumero() {
        String numero;
        do {
            numero = String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        } while (cuentaRepository.existsByNumero(numero));
        return numero;
    }
}
