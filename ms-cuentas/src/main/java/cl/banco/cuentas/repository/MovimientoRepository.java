package cl.banco.cuentas.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cl.banco.cuentas.model.MovimientoCuenta;

public interface MovimientoRepository extends JpaRepository<MovimientoCuenta, Long> {

    Optional<MovimientoCuenta> findByCuentaIdAndReferenciaAndTipo(Long cuentaId, String referencia, String tipo);

    List<MovimientoCuenta> findByCuentaIdOrderByFechaDesc(Long cuentaId);
}
