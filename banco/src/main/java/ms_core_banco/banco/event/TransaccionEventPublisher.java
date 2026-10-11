package ms_core_banco.banco.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransaccionEventPublisher {

    private static final String TOPIC = "transacciones-realizadas";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publicar(TransaccionRealizadaEvent evento) {
        try {
            String payload = objectMapper.writeValueAsString(evento);
            // La key es el id de cuenta: asi Kafka garantiza que todos los eventos
            // de una misma cuenta van a la misma particion y se procesan en orden.
            kafkaTemplate.send(TOPIC, evento.cuentaId().toString(), payload);
            log.info("Evento publicado en '{}': {}", TOPIC, payload);
        } catch (Exception e) {
            // Importante: un problema con Kafka NUNCA debe tumbar una transaccion
            // que ya se confirmo y persistio en la base de datos. Por eso solo logueamos.
            log.error("No se pudo publicar el evento de transaccion realizada", e);
        }
    }
}