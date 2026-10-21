package com.upc.Repository;

import com.upc.Entity.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminRepository extends JpaRepository<AdminUser, Long> {
    Optional<AdminUser> findByCorreo(String correo);
    boolean existsByCorreo(String correo);
}
