package com.upc.Controller;

import com.upc.DTO.Auth.AdminAuthResponseDTO;
import com.upc.DTO.Auth.AdminCambiarPasswordDTO;
import com.upc.DTO.Auth.AdminLoginDTO;
import com.upc.DTO.Auth.AdminRegistroDTO;
import com.upc.Entity.AdminUser;
import com.upc.Repository.AdminRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({"/api/v1/auth/admin", "/api/auth/admin"})
@CrossOrigin(origins = {"http://localhost:4200", "https://puntoycoma-store.netlify.app", "https://puntoycoma-store.netlify.app/"})
public class AdminAuthController {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminAuthController(AdminRepository adminRepository, PasswordEncoder passwordEncoder) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/registro")
    public ResponseEntity<?> registrar(@RequestBody AdminRegistroDTO dto) {
        try {
            if (dto.getNombreCompleto() == null || dto.getCorreo() == null || dto.getPassword() == null) {
                return ResponseEntity.badRequest().body(Map.of("mensaje", "Todos los campos son obligatorios"));
            }

            String correo = dto.getCorreo().trim();
            if (adminRepository.existsByCorreo(correo)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("mensaje", "El correo ya se encuentra registrado"));
            }

            String passwordEncriptada = passwordEncoder.encode(dto.getPassword());
            AdminUser nuevoAdmin = new AdminUser(
                    dto.getNombreCompleto().trim(),
                    correo,
                    passwordEncriptada
            );

            adminRepository.save(nuevoAdmin);

            return ResponseEntity.status(HttpStatus.CREATED).body(new AdminAuthResponseDTO(
                    true,
                    "Administrador registrado con éxito",
                    nuevoAdmin.getId(),
                    nuevoAdmin.getNombreCompleto(),
                    nuevoAdmin.getCorreo(),
                    nuevoAdmin.getRol()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("mensaje", "Error interno al registrar el administrador: " + e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AdminLoginDTO dto) {
        try {
            if (dto.getCorreo() == null || dto.getPassword() == null) {
                return ResponseEntity.badRequest().body(Map.of("mensaje", "Correo y contraseña requeridos"));
            }

            String correo = dto.getCorreo().trim();
            AdminUser admin = adminRepository.findByCorreo(correo)
                    .orElse(null);

            if (admin == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("mensaje", "Credenciales inválidas: correo no registrado"));
            }

            if (!passwordEncoder.matches(dto.getPassword(), admin.getPassword())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("mensaje", "Credenciales inválidas: contraseña incorrecta"));
            }

            return ResponseEntity.ok(new AdminAuthResponseDTO(
                    true,
                    "Inicio de sesión exitoso",
                    admin.getId(),
                    admin.getNombreCompleto(),
                    admin.getCorreo(),
                    admin.getRol()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("mensaje", "Error al procesar el inicio de sesión: " + e.getMessage()));
        }
    }

    @PutMapping("/cambiar-password")
    public ResponseEntity<?> cambiarPassword(@RequestBody AdminCambiarPasswordDTO dto) {
        try {
            if (dto.getCorreo() == null || dto.getPasswordActual() == null || dto.getPasswordNueva() == null) {
                return ResponseEntity.badRequest().body(Map.of("mensaje", "Todos los campos son obligatorios"));
            }

            AdminUser admin = adminRepository.findByCorreo(dto.getCorreo().trim())
                    .orElse(null);

            if (admin == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("mensaje", "Administrador no encontrado"));
            }

            if (!passwordEncoder.matches(dto.getPasswordActual(), admin.getPassword())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("mensaje", "La contraseña actual no es correcta"));
            }

            admin.setPassword(passwordEncoder.encode(dto.getPasswordNueva()));
            adminRepository.save(admin);

            return ResponseEntity.ok(new AdminAuthResponseDTO(
                    true,
                    "Contraseña actualizada exitosamente",
                    admin.getId(),
                    admin.getNombreCompleto(),
                    admin.getCorreo(),
                    admin.getRol()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("mensaje", "Error al actualizar contraseña: " + e.getMessage()));
        }
    }
}
