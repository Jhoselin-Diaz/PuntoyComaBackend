package com.upc.Repository;

import com.upc.Entity.Cupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CuponRepository extends JpaRepository<Cupon, Long> {
    Optional<Cupon> findByCodigoIgnoreCaseAndActivoTrue(String codigo);
}
