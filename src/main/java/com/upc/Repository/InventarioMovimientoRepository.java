package com.upc.Repository;

import com.upc.Entity.InventarioMovimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface InventarioMovimientoRepository extends JpaRepository<InventarioMovimiento, Long> {
    List<InventarioMovimiento> findByPedidoId(Long pedidoId);
    List<InventarioMovimiento> findByPedidoIdAndTipoMovimiento(Long pedidoId, String tipoMovimiento);
}
