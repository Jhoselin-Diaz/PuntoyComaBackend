package com.upc.Repository;

import com.upc.Entity.PedidoDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PedidoDetalleRepository extends JpaRepository<PedidoDetalle, Long> {
    @Query("SELECT pd FROM PedidoDetalle pd JOIN FETCH pd.producto JOIN FETCH pd.pedido WHERE pd.pedido.usuario.telefono = :telefono")
    List<PedidoDetalle> findByUsuarioTelefono(@Param("telefono") String telefono);
    
    List<PedidoDetalle> findByPedidoId(Long pedidoId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM PedidoDetalle pd WHERE pd.pedido.id = :pedidoId")
    void deleteByPedidoId(@org.springframework.data.repository.query.Param("pedidoId") Long pedidoId);
}
