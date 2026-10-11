package cl.banco.clientes.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cl.banco.clientes.model.AlertaSeguridad;

public interface AlertaRepository extends JpaRepository<AlertaSeguridad, Long> {
    List<AlertaSeguridad> findByClienteIdOrderByFechaDesc(Long clienteId);
}
