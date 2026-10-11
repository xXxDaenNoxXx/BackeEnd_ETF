package ms_core_banco.banco.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import ms_core_banco.banco.model.CuentaInteres;

public interface CuentaInteresRepository extends JpaRepository<CuentaInteres, Long> {
}