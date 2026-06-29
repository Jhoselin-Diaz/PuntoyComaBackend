package com.upc.Repository;

import com.upc.Entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {
    Optional<Chat> findByTelefonoCliente(String telefonoCliente);
    List<Chat> findAllByOrderByFechaUltimaActualizacionDesc();

    @Query("SELECT c FROM Chat c ORDER BY " +
           "CASE WHEN c.prioridad = 'ALTA' THEN 1 " +
           "     WHEN c.prioridad = 'INTERMEDIA' THEN 2 " +
           "     WHEN c.prioridad = 'BAJA' THEN 3 " +
           "     ELSE 4 END ASC, " +
           "c.fechaUltimaActualizacion DESC")
    List<Chat> findAllChatsOrderedByPriorityAndDate();
}
