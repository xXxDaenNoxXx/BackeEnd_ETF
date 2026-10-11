package cl.banco.cuentas.model;

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

@Entity
@Table(name = "cuentas")
@Getter
@Setter
@NoArgsConstructor
public class Cuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String numero;

    @Column(nullable = false)
    private Long clienteId;

    /** CORRIENTE | AHORRO | VISTA */
    @Column(nullable = false, length = 12)
    private String tipo;

    @Column(length = 60)
    private String alias;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal saldo;

    /** ACTIVA | CERRADA */
    @Column(nullable = false, length = 10)
    private String estado;

    @Column(nullable = false)
    private LocalDateTime creadaEn;

    private LocalDateTime cerradaEn;
}
