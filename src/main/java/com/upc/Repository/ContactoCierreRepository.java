package com.upc.Repository;

import com.upc.Entity.ContactoCierre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContactoCierreRepository extends JpaRepository<ContactoCierre, Long> {
}
