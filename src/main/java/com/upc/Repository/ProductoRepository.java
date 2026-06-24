package com.upc.Repository;

import com.upc.Entity.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {
    List<Producto> findByActivoTrue();
    List<Producto> findByVisibleTrue();
    List<Producto> findByCategoriaAndActivoTrue(String categoria);
    List<Producto> findByStockLessThanEqualAndActivoTrue(Integer umbral);
}
