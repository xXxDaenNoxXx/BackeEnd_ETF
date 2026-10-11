package cl.banco.cuentas.model;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Proyeccion alimentada por el consumidor Kafka (transacciones.completadas): actividad por cuenta. */
@Entity
@Table(name = "actividad_cuenta")
@Getter
@Setter
@NoArgsConstructor
public class ActividadCuenta {

    @Id
    private Long cuentaId;

    private long totalTransacciones;

    private LocalDateTime ultimaActividad;
}
