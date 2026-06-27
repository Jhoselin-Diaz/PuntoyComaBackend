package com.upc.Repository;

import com.upc.Entity.CategoriaDestacada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoriaDestacadaRepository extends JpaRepository<CategoriaDestacada, Long> {
}
