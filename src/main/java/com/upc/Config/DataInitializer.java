package com.upc.Config;

import com.upc.Entity.AdminUser;
import com.upc.Repository.AdminRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initAdminDefault(AdminRepository adminRepo, PasswordEncoder encoder) {
        return args -> {
            String correoAdmin = "admin@puntoycoma.com";
            if (!adminRepo.existsByCorreo(correoAdmin)) {
                AdminUser defaultAdmin = new AdminUser(
                    "Administrador",
                    correoAdmin,
                    encoder.encode("admin123")
                );
                adminRepo.save(defaultAdmin);
                System.out.println("✅ Administrador por defecto inicializado: admin@puntoycoma.com / admin123");
            }
        };
    }
}
