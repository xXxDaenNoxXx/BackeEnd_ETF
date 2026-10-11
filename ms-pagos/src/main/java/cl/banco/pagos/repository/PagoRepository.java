package cl.banco.pagos.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cl.banco.pagos.model.Pago;

public interface PagoRepository extends JpaRepository<Pago, String> {

    List<Pago> findByEstado(String estado);

    List<Pago> findByCuentaOrigenIdOrCuentaDestinoIdOrderByCreadoEnDesc(Long origen, Long destino);
}
