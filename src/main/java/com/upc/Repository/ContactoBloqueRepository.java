package com.upc.Repository;

import com.upc.Entity.ContactoBloque;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContactoBloqueRepository extends JpaRepository<ContactoBloque, String> {
}
