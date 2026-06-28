package com.upc.Repository;

import com.upc.Entity.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {
    List<Producto> findByActivoTrue();
    List<Producto> findByVisibleTrue();
    List<Producto> findByCategoriaAndActivoTrue(String categoria);
    List<Producto> findByStockLessThanEqualAndActivoTrue(Integer umbral);

    @Query("SELECT p FROM Producto p WHERE p.visible = true ORDER BY COALESCE(p.vistasContador, 0) DESC, p.id DESC")
    List<Producto> findTop5MasVistos();

    @Query("SELECT p FROM Producto p ORDER BY COALESCE(p.vistasContador, 0) DESC, p.nombre ASC")
    List<Producto> findAllOrderByVistasContadorDesc();
}
