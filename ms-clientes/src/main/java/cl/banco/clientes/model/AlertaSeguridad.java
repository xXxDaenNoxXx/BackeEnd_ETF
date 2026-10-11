package cl.banco.clientes.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Alerta recibida por Kafka (topico alertas.seguridad) y asociada al cliente afectado. */
@Entity
@Table(name = "alertas_seguridad")
@Getter
@Setter
@NoArgsConstructor
public class AlertaSeguridad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long clienteId;
    private Long cuentaId;

    @Column(length = 30)
    private String tipo;

    private BigDecimal monto;

    @Column(length = 250)
    private String mensaje;

    private LocalDateTime fecha;
}
