package com.upc.Config;

import com.upc.Entity.*;
import com.upc.Repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    public CommandLineRunner initDatabaseData(
            AdminRepository adminRepo,
            UsuarioRepository usuarioRepo,
            ProductoRepository productoRepo,
            HomeBannerRepository bannerRepo,
            ContactoBloqueRepository contactoBloqueRepo,
            ContactoCierreRepository contactoCierreRepo,
            PasswordEncoder encoder) {

        return args -> {
            log.info("🚀 [DataInitializer] Verificando datos iniciales del sistema...");

            // 1. Admins por defecto para login en Netlify (/api/v1/auth/admin/login)
            seedAdminIfNotExists(adminRepo, "Administrador", "admin@puntoycoma.com", encoder.encode("admin123"));
            seedAdminIfNotExists(adminRepo, "Jhoselin Díaz", "jhoselin@puntoycoma.com", encoder.encode("admin123"));
            seedAdminIfNotExists(adminRepo, "Jhoselin Díaz", "xvantx2@gmail.com", encoder.encode("admin123"));
            seedAdminIfNotExists(adminRepo, "Jhoselin", "jhoselin@gmail.com", encoder.encode("admin123"));


            // 2. Usuarios del sistema para gestión de chats y auditoría
            seedUsuarioIfNotExists(usuarioRepo, "Admin Principal", "admin@puntoycoma.com", encoder.encode("admin123"), "ADMIN");
            seedUsuarioIfNotExists(usuarioRepo, "Jhoselin Díaz", "jhoselin@puntoycoma.com", encoder.encode("admin123"), "ADMIN");

            // 3. Catálogo de productos iniciales
            if (productoRepo.count() == 0) {
                log.info("📦 [DataInitializer] Catálogo de productos vacío. Insertando productos iniciales...");

                Producto p1 = new Producto();
                p1.setNombre("Taza Nube");
                p1.setDescripcion("Taza aesthetic en forma de nube. Elaborada con cerámica de alta calidad y acabado suave al tacto. Ideal para café o té con estilo.");
                p1.setPrecio(38.0);
                p1.setStock(25);
                p1.setCategoria("Tazas");
                p1.setCodigoSku("PC-001");
                p1.setImageUrl("https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?q=80&w=800&auto=format&fit=crop");
                p1.setImagenPrincipal("https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?q=80&w=800&auto=format&fit=crop");
                p1.setCapacidad("350 ml");
                p1.setMaterial("Cerámica de alta temperatura");
                p1.setActivo(true);
                p1.setVisible(true);
                p1.setDestacado(true);
                p1.setNuevo(true);
                p1.setAptoMicroondas(true);
                p1.setAptoLavavajillas(true);
                p1.setGradoAlimentario(true);
                p1.setVistasContador(0);

                Producto p2 = new Producto();
                p2.setNombre("Vaso con Sorbete");
                p2.setDescripcion("Vaso aesthetic con tapa de bambú natural y sorbete de vidrio reutilizable. Ideal para café frío, té helado, matcha o jugos frescos.");
                p2.setPrecio(38.0);
                p2.setStock(20);
                p2.setCategoria("Vasos");
                p2.setCodigoSku("PC-002");
                p2.setImageUrl("https://images.unsplash.com/photo-1577937927133-66ef06acdf18?q=80&w=800&auto=format&fit=crop");
                p2.setImagenPrincipal("https://images.unsplash.com/photo-1577937927133-66ef06acdf18?q=80&w=800&auto=format&fit=crop");
                p2.setCapacidad("450 ml");
                p2.setMaterial("Vidrio borosilicato y bambú");
                p2.setActivo(true);
                p2.setVisible(true);
                p2.setDestacado(true);
                p2.setNuevo(false);
                p2.setAptoTemperaturas(true);
                p2.setGradoAlimentario(true);
                p2.setVistasContador(0);

                Producto p3 = new Producto();
                p3.setNombre("Taza Artesanal de Arcilla");
                p3.setDescripcion("Taza moldeada a mano por artesanos peruanos. Acabado rústico contemporáneo con textura terrosa y esmaltado interior brillante.");
                p3.setPrecio(42.0);
                p3.setStock(15);
                p3.setCategoria("Tazas");
                p3.setCodigoSku("PC-003");
                p3.setImageUrl("https://images.unsplash.com/photo-1517256064527-09c73fc73e38?q=80&w=800&auto=format&fit=crop");
                p3.setImagenPrincipal("https://images.unsplash.com/photo-1517256064527-09c73fc73e38?q=80&w=800&auto=format&fit=crop");
                p3.setCapacidad("300 ml");
                p3.setMaterial("Arcilla natural horneada");
                p3.setActivo(true);
                p3.setVisible(true);
                p3.setDestacado(true);
                p3.setNuevo(true);
                p3.setAptoMicroondas(true);
                p3.setGradoAlimentario(true);
                p3.setVistasContador(0);

                Producto p4 = new Producto();
                p4.setNombre("Taza Rosa Aesthetic");
                p4.setDescripcion("Taza de cerámica minimalista en tonalidad rosa pastel mate con asa ergonómica oversize para un agarre cómodo y fotogénico.");
                p4.setPrecio(35.0);
                p4.setStock(18);
                p4.setCategoria("Tazas");
                p4.setCodigoSku("PC-004");
                p4.setImageUrl("https://images.unsplash.com/photo-1536939459926-301728717817?q=80&w=800&auto=format&fit=crop");
                p4.setImagenPrincipal("https://images.unsplash.com/photo-1536939459926-301728717817?q=80&w=800&auto=format&fit=crop");
                p4.setCapacidad("320 ml");
                p4.setMaterial("Cerámica esmaltada");
                p4.setActivo(true);
                p4.setVisible(true);
                p4.setDestacado(false);
                p4.setNuevo(false);
                p4.setAptoMicroondas(true);
                p4.setAptoLavavajillas(true);
                p4.setGradoAlimentario(true);
                p4.setVistasContador(0);

                Producto p5 = new Producto();
                p5.setNombre("Vaso Térmico Minimalista");
                p5.setDescripcion("Vaso térmico doble capa con aislamiento al vacío. Mantiene tus bebidas frías por 12 horas o calientes por 6 horas.");
                p5.setPrecio(45.0);
                p5.setStock(12);
                p5.setCategoria("Vasos");
                p5.setCodigoSku("PC-005");
                p5.setImageUrl("https://images.unsplash.com/photo-1570784332176-fdd73da66f03?q=80&w=800&auto=format&fit=crop");
                p5.setImagenPrincipal("https://images.unsplash.com/photo-1570784332176-fdd73da66f03?q=80&w=800&auto=format&fit=crop");
                p5.setCapacidad("500 ml");
                p5.setMaterial("Acero inoxidable 304");
                p5.setActivo(true);
                p5.setVisible(true);
                p5.setDestacado(true);
                p5.setNuevo(true);
                p5.setAptoTemperaturas(true);
                p5.setGradoAlimentario(true);
                p5.setVistasContador(0);

                productoRepo.saveAll(List.of(p1, p2, p3, p4, p5));
                log.info("✅ [DataInitializer] Catálogo inicial cargado con éxito: 5 productos.");
            }

            // 4. Banner principal del Home
            if (bannerRepo.count() == 0) {
                HomeBanner banner = new HomeBanner();
                banner.setTitulo("Elegantes Tazas & Vasos Aesthetic");
                banner.setSubtitulo("Dale un toque premium a tus mañanas con nuestra colección exclusiva elaborada por artesanos.");
                banner.setTextoBoton("Explorar Colección");
                banner.setLinkBoton("/productos");
                banner.setImagenUrl("https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?q=80&w=1920&auto=format&fit=crop");
                banner.setVisible(true);
                bannerRepo.save(banner);
                log.info("✅ [DataInitializer] Banner principal de Home inicializado.");
            }

            // 5. Bloques de Contacto
            if (contactoBloqueRepo.count() == 0) {
                contactoBloqueRepo.saveAll(List.of(
                        new ContactoBloque("block-wa", "WhatsApp", "Escríbenos y te respondemos lo antes posible.", "whatsapp", "Escríbenos por WhatsApp", "https://wa.me/51999999999", true),
                        new ContactoBloque("block-ig", "Instagram", "Síguenos y descubre nuestras novedades.", "instagram", "@Brandname", "https://instagram.com/", true),
                        new ContactoBloque("block-support", "Atención al Cliente", "Estamos para ayudarte en lo que necesites.", "support", "Lun - Sáb: 9:00 am - 6:00 pm", "", true),
                        new ContactoBloque("block-email", "Email", "Escríbenos y te responderemos lo antes posible.", "email", "hola@brandname.com", "mailto:hola@brandname.com", true),
                        new ContactoBloque("block-info", "Información", "Resolvemos tus dudas sobre productos, pedidos, envíos y más.", "info", "", "", true)
                ));
                log.info("✅ [DataInitializer] Bloques de contacto inicializados.");
            }

            if (contactoCierreRepo.count() == 0) {
                ContactoCierre cierre = new ContactoCierre(null, "¿Dudas sobre tu pedido?", "51999999999", "Hola, tengo una consulta sobre un pedido.", true);
                contactoCierreRepo.save(cierre);
                log.info("✅ [DataInitializer] Cierre de contacto inicializado.");
            }

            log.info("🎉 [DataInitializer] Inicialización de datos verificada y completa.");
        };
    }

    private void seedAdminIfNotExists(AdminRepository adminRepo, String nombre, String correo, String encodedPassword) {
        if (!adminRepo.existsByCorreo(correo)) {
            AdminUser admin = new AdminUser(nombre, correo, encodedPassword);
            adminRepo.save(admin);
            log.info("✅ [DataInitializer] Admin inicializado: {} ({})", nombre, correo);
        }
    }

    private void seedUsuarioIfNotExists(UsuarioRepository usuarioRepo, String nombre, String email, String encodedPassword, String rol) {
        if (!usuarioRepo.existsByEmail(email)) {
            Usuario u = new Usuario();
            u.setNombre(nombre);
            u.setEmail(email);
            u.setPassword(encodedPassword);
            u.setRol(rol);
            u.setActivo(true);
            u.setHabilitado(true);
            u.setIntentosFallidos(0);
            u.setCuentaBloqueada(false);
            usuarioRepo.save(u);
            log.info("✅ [DataInitializer] Usuario inicializado: {} ({})", nombre, email);
        }
    }
}
