package cl.banco.cuentas.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import cl.banco.cuentas.model.ActividadCuenta;

public interface ActividadRepository extends JpaRepository<ActividadCuenta, Long> {
}
