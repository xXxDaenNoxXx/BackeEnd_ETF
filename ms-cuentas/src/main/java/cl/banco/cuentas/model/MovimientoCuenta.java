package cl.banco.cuentas.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Libro de movimientos. La restriccion unica (cuenta, referencia, tipo) hace IDEMPOTENTE cada operacion:
 * si ms-pagos repite una llamada (reintento tras un timeout) no se debita/acredita dos veces.
 */
@Entity
@Table(name = "movimientos_cuenta",
        uniqueConstraints = @UniqueConstraint(name = "uk_mov_cuenta_ref_tipo", columnNames = {"cuentaId", "referencia", "tipo"}))
@Getter
@Setter
@NoArgsConstructor
public class MovimientoCuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long cuentaId;

    /** DEBITO | CREDITO */
    @Column(nullable = false, length = 8)
    private String tipo;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal saldoResultante;

    @Column(nullable = false, length = 80)
    private String referencia;

    @Column(nullable = false)
    private LocalDateTime fecha;
}
