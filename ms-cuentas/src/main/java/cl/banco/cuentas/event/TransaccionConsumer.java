package cl.banco.cuentas.event;

import java.time.LocalDateTime;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import cl.banco.cuentas.model.ActividadCuenta;
import cl.banco.cuentas.repository.ActividadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * CONSUMIDOR en tiempo real de transacciones.completadas (las produce ms-pagos):
 * mantiene una proyeccion de actividad por cuenta (total de transacciones y ultima actividad).
 * La clave del evento es el id de cuenta => Kafka garantiza orden por cuenta; con varias replicas del
 * servicio cada una atiende un subconjunto de particiones (mismo groupId).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransaccionConsumer {

    private final ActividadRepository actividadRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${cuentas.topic-transacciones}", groupId = "${cuentas.grupo-consumidor}")
    public void transaccionCompletada(String payload) {
        try {
            TransaccionCompletadaEvent e = objectMapper.readValue(payload, TransaccionCompletadaEvent.class);
            registrar(e.cuentaOrigenId());
            registrar(e.cuentaDestinoId());
            log.info("Transaccion {} ({}) por {} registrada en la actividad de las cuentas", e.pagoId(), e.tipo(), e.monto());
        } catch (Exception ex) {
            log.error("No se pudo procesar la transaccion recibida: {}", ex.getMessage());
        }
    }

    private void registrar(Long cuentaId) {
        if (cuentaId == null) {
            return;
        }
        ActividadCuenta a = actividadRepository.findById(cuentaId).orElseGet(() -> {
            ActividadCuenta nueva = new ActividadCuenta();
            nueva.setCuentaId(cuentaId);
            return nueva;
        });
        a.setTotalTransacciones(a.getTotalTransacciones() + 1);
        a.setUltimaActividad(LocalDateTime.now());
        actividadRepository.save(a);
    }
}
