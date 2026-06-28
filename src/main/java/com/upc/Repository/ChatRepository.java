package com.upc.Repository;

import com.upc.Entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {
    Optional<Chat> findByTelefonoCliente(String telefonoCliente);
    List<Chat> findAllByOrderByFechaUltimaActualizacionDesc();
}
