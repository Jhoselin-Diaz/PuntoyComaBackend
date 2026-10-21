package com.upc.Repository;

import com.upc.Entity.PagoVoucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PagoVoucherRepository extends JpaRepository<PagoVoucher, Long> {

    List<PagoVoucher> findByPedidoIdOrderByFechaPagoAsc(Long pedidoId);

    boolean existsByWamid(String wamid);

    boolean existsByPedidoIdAndVoucherUrl(Long pedidoId, String voucherUrl);

    @Query("SELECT COALESCE(SUM(pv.monto), 0.0) FROM PagoVoucher pv WHERE pv.pedido.id = :pedidoId")
    Double obtenerMontoTotalPagadoPorPedido(@Param("pedidoId") Long pedidoId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM PagoVoucher pv WHERE pv.pedido.id = :pedidoId")
    void deleteByPedidoId(@Param("pedidoId") Long pedidoId);
}

