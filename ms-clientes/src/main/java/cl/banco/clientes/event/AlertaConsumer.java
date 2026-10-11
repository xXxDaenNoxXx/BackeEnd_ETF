package cl.banco.clientes.event;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import cl.banco.clientes.model.AlertaSeguridad;
import cl.banco.clientes.repository.AlertaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * CONSUMIDOR de alertas de seguridad (las produce ms-pagos). ms-clientes las asocia al cliente para
 * que el equipo de fraude y el cliente puedan consultarlas; ms-pagos no sabe que este consumidor existe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertaConsumer {

    private final AlertaRepository alertaRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${clientes.topic-alertas}", groupId = "${spring.kafka.consumer.group-id}")
    public void alertaRecibida(String payload) {
        try {
            AlertaSeguridadEvent e = objectMapper.readValue(payload, AlertaSeguridadEvent.class);
            AlertaSeguridad a = new AlertaSeguridad();
            a.setClienteId(e.clienteId());
            a.setCuentaId(e.cuentaId());
            a.setTipo(e.tipo());
            a.setMonto(e.monto());
            a.setMensaje(e.mensaje());
            a.setFecha(e.fecha());
            alertaRepository.save(a);
            log.warn("ALERTA DE SEGURIDAD registrada -> cliente {} cuenta {}: {} ({})",
                    e.clienteId(), e.cuentaId(), e.mensaje(), e.tipo());
        } catch (Exception ex) {
            log.error("No se pudo procesar la alerta recibida: {}", ex.getMessage());
        }
    }
}
