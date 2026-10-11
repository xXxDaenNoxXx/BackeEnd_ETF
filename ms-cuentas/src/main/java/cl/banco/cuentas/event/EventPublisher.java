package cl.banco.cuentas.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publicar(String topico, String clave, Object evento) {
        try {
            kafkaTemplate.send(topico, clave, objectMapper.writeValueAsString(evento));
            log.info("Evento publicado en '{}' (clave {})", topico, clave);
        } catch (Exception e) {
            log.error("No se pudo publicar el evento en '{}': {}", topico, e.getMessage());
        }
    }
}
