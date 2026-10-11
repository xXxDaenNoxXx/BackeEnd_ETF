package ms_core_banco.banco.repository;


import org.springframework.data.jpa.repository.JpaRepository;

import ms_core_banco.banco.model.MovimientoAnual;

import java.util.List;

public interface MovimientoAnualRepository extends JpaRepository<MovimientoAnual, Long> {
    List<MovimientoAnual> findByCuentaId(Long cuentaId);
}