package bff_cajeros.banco.event;
 
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
 
import com.fasterxml.jackson.databind.ObjectMapper;
 
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
 
@Component
@RequiredArgsConstructor
@Slf4j
public class TransaccionEventListener {
 
    private final ObjectMapper objectMapper;
 
    @KafkaListener(topics = "transacciones-realizadas", groupId = "bff-cajeros")
    public void escuchar(String payload) {
        try {
            TransaccionRealizadaEvent evento = objectMapper.readValue(payload, TransaccionRealizadaEvent.class);
            // Aqui se simula el consumidor "de negocio": comprobante, notificacion o auditoria.
            // En un caso real esto podria enviar un correo/SMS o guardar un registro de auditoria
            // en una base de datos separada, sin que ms-core-banco sepa que este consumidor existe.
            log.info("COMPROBANTE generado -> cuenta {}: {} de {} | saldo resultante: {} | fecha: {}",
                    evento.cuentaId(), evento.tipo(), evento.monto(), evento.saldoResultante(), evento.fecha());
        } catch (Exception e) {
            log.error("No se pudo procesar el evento de transaccion recibido", e);
        }
    }
}