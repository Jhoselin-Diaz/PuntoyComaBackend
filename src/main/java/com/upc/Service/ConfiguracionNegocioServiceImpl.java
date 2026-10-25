package com.upc.Service;

import com.upc.Entity.ConfiguracionNegocio;
import com.upc.Repository.ConfiguracionNegocioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ConfiguracionNegocioServiceImpl implements ConfiguracionNegocioService {

    @org.springframework.beans.factory.annotation.Value("${supabase.url:https://muozenxfcbddxpjnbhsx.supabase.co}")
    private String supabaseUrl;

    @org.springframework.beans.factory.annotation.Value("${supabase.key:}")
    private String supabaseKey;

    @Autowired
    private ConfiguracionNegocioRepository repository;

    @Override
    public ConfiguracionNegocio obtenerConfiguracion() {
        return repository.findById(1L).orElseGet(() -> {
            ConfiguracionNegocio def = new ConfiguracionNegocio();
            def.setNombreTienda("Punto y Coma");
            def.setCorreo("contacto@puntoycoma.com");
            def.setTelefono("+51 999 999 999");
            def.setWhatsapp("+51 999 999 999");
            def.setInstagram("puntoycoma");
            def.setDireccion("Lima, Perú");
            def.setColorPrincipal("#df6c1b");
            def.setSugerirAcciones(true);
            def.setValidarPagos(true);
            def.setDetectarIncompletos(true);
            def.setClasificarConversaciones(true);
            def.setBannerTitle("Ofertas Especiales");
            def.setBannerSubtitle("Aprovecha estas promociones exclusivas...");
            def.setBannerImage("https://images.unsplash.com/photo-1577918349257-2e1d7fbebf96");
            def.setBannerVisible(true);
            
            def.setOfertasBannerTitulo("Ofertas Especiales");
            def.setOfertasBannerSubtitulo("Aprovecha estas promociones exclusivas...");
            def.setOfertasBannerImg("https://images.unsplash.com/photo-1577918349257-2e1d7fbebf96");
            
            return repository.save(def);
        });
    }

    @Override
    public ConfiguracionNegocio guardarConfiguracion(ConfiguracionNegocio configuracion) {
        ConfiguracionNegocio actual = obtenerConfiguracion();
        if (configuracion.getNombreTienda() != null) actual.setNombreTienda(configuracion.getNombreTienda());
        if (configuracion.getWhatsapp() != null) actual.setWhatsapp(configuracion.getWhatsapp());
        if (configuracion.getColorPrincipal() != null) actual.setColorPrincipal(configuracion.getColorPrincipal());
        if (configuracion.getLogoUrl() != null) actual.setLogoUrl(configuracion.getLogoUrl());
        if (configuracion.getPortadaUrl() != null) actual.setPortadaUrl(configuracion.getPortadaUrl());
        if (configuracion.getCorreo() != null) actual.setCorreo(configuracion.getCorreo());
        if (configuracion.getTelefono() != null) actual.setTelefono(configuracion.getTelefono());
        if (configuracion.getInstagram() != null) actual.setInstagram(configuracion.getInstagram());
        if (configuracion.getDireccion() != null) actual.setDireccion(configuracion.getDireccion());
        if (configuracion.getOfertasBannerTitulo() != null) actual.setOfertasBannerTitulo(configuracion.getOfertasBannerTitulo());
        if (configuracion.getOfertasBannerSubtitulo() != null) actual.setOfertasBannerSubtitulo(configuracion.getOfertasBannerSubtitulo());
        if (configuracion.getOfertasBannerImg() != null) actual.setOfertasBannerImg(configuracion.getOfertasBannerImg());
        return repository.save(actual);
    }

    @Override
    public ConfiguracionNegocio subirLogo(org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo del logo no puede estar vacío");
        }
        String extension = ".png";
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String uniqueName = "logo_" + java.util.UUID.randomUUID().toString() + extension;
        String uploadUrl = supabaseUrl + "/storage/v1/object/productos/" + uniqueName;

        try {
            byte[] bytes = file.getBytes();
            String contentType = file.getContentType();
            if (contentType == null || contentType.isEmpty()) {
                contentType = "image/png";
            }

            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(uploadUrl))
                    .header("apikey", supabaseKey)
                    .header("Authorization", "Bearer " + supabaseKey)
                    .header("Content-Type", contentType)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(bytes))
                    .build();

            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("Respuesta fallida de Supabase Storage: " + response.body());
            }

            String publicLogoUrl = supabaseUrl + "/storage/v1/object/public/productos/" + uniqueName;
            ConfiguracionNegocio actual = obtenerConfiguracion();
            actual.setLogoUrl(publicLogoUrl);
            return repository.save(actual);
        } catch (Exception e) {
            throw new RuntimeException("Fallo al subir el logo a Supabase Storage: " + e.getMessage(), e);
        }
    }
}
