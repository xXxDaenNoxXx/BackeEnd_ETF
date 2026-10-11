package ms_core_banco.banco.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "cuentas_interes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CuentaInteres {

    @Id
    private Long cuentaId;

    private String nombre;
    private BigDecimal saldo;
    private Integer edad;
    private String tipo; // ahorro, prestamo, hipoteca
}