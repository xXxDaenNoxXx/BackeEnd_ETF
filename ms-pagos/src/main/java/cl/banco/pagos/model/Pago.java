package cl.banco.pagos.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una operacion de pago. El id ES la clave de idempotencia (UUID): repetir la misma peticion
 * devuelve la operacion ya registrada y nunca la ejecuta dos veces.
 *
 * Estados: PENDIENTE -> COMPLETADO | RECHAZADO | PENDIENTE_COMPENSACION -> COMPENSADO
 */
@Entity
@Table(name = "pagos")
@Getter
@Setter
@NoArgsConstructor
public class Pago {

    @Id
    @Column(length = 64)
    private String id;

    /** PAGO | TRANSFERENCIA | DEPOSITO */
    @Column(nullable = false, length = 15)
    private String tipo;

    private Long cuentaOrigenId;
    private Long cuentaDestinoId;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal monto;

    @Column(length = 120)
    private String descripcion;

    @Column(nullable = false, length = 25)
    private String estado;

    @Column(length = 250)
    private String motivo;

    @Column(nullable = false)
    private LocalDateTime creadoEn;

    private LocalDateTime actualizadoEn;
}
