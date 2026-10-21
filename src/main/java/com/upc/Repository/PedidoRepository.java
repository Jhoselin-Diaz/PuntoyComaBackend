package com.upc.Repository;

import com.upc.Entity.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {
    List<Pedido> findByUsuarioTelefono(String telefono);
    java.util.Optional<Pedido> findByNumeroOrden(String numeroOrden);
    Pedido findFirstByUsuarioTelefonoAndEstado(String telefono, String estado);
    Pedido findTopByUsuarioTelefonoAndEstadoOrderByFechaDesc(String telefono, String estado);
    List<Pedido> findByEstadoAndFechaBefore(String estado, java.time.LocalDateTime limite);
}
