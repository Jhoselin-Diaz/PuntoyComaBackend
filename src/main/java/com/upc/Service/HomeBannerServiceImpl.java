package com.upc.Service;

import com.upc.DTO.HomeBannerDTO;
import com.upc.Entity.HomeBanner;
import com.upc.Repository.HomeBannerRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class HomeBannerServiceImpl implements HomeBannerService {

    @Autowired
    private HomeBannerRepository homeBannerRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key:}")
    private String supabaseKey;

    @Override
    @Transactional(readOnly = true)
    public HomeBannerDTO obtenerBanner() {
        List<HomeBanner> banners = homeBannerRepository.findAll();
        if (banners.isEmpty()) {
            HomeBanner bannerInicial = new HomeBanner();
            bannerInicial.setTitulo("Elegantes Tazas & Vasos Aesthetic");
            bannerInicial.setSubtitulo("Dale un toque premium a tus mañanas con nuestra colección exclusiva elaborada por artesanos.");
            bannerInicial.setTextoBoton("Explorar Colección");
            bannerInicial.setLinkBoton("/productos");
            bannerInicial.setImagenUrl("https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?q=80&w=1920&auto=format&fit=crop");
            bannerInicial.setVisible(true);
            bannerInicial.setActualizadoEn(LocalDateTime.now());
            
            HomeBanner guardado = homeBannerRepository.save(bannerInicial);
            return modelMapper.map(guardado, HomeBannerDTO.class);
        }
        return modelMapper.map(banners.get(0), HomeBannerDTO.class);
    }

    @Override
    @Transactional
    public HomeBannerDTO actualizarBanner(HomeBannerDTO bannerDTO, MultipartFile file) {
        List<HomeBanner> banners = homeBannerRepository.findAll();
        HomeBanner banner;
        if (banners.isEmpty()) {
            banner = new HomeBanner();
        } else {
            banner = banners.get(0);
        }

        banner.setTitulo(bannerDTO.getTitulo());
        banner.setSubtitulo(bannerDTO.getSubtitulo());
        banner.setTextoBoton(bannerDTO.getTextoBoton());
        banner.setLinkBoton(bannerDTO.getLinkBoton());
        
        // Si se recibe un archivo físico nuevo, se sube a Supabase Storage (mismo bucket de productos)
        if (file != null && !file.isEmpty()) {
            try {
                String supabaseImageUrl = subirImagenASupabase(file);
                banner.setImagenUrl(supabaseImageUrl);
            } catch (Exception e) {
                throw new RuntimeException("Error al subir el archivo de banner a Supabase Storage: " + e.getMessage(), e);
            }
        } else if (bannerDTO.getImagenUrl() != null && !bannerDTO.getImagenUrl().trim().isEmpty()) {
            // Mantiene la URL existente (la de Google Drive del Picker o la actual de Supabase)
            banner.setImagenUrl(bannerDTO.getImagenUrl());
        }
        
        banner.setVisible(bannerDTO.getVisible());
        banner.setActualizadoEn(LocalDateTime.now());

        HomeBanner actualizado = homeBannerRepository.save(banner);
        return modelMapper.map(actualizado, HomeBannerDTO.class);
    }

    /**
     * Sube un archivo a Supabase Storage en el bucket 'productos', adaptado de la lógica de ProductoServiceImpl
     */
    private String subirImagenASupabase(MultipartFile file) {
        try {
            String extension = ".jpg";
            String originalFilename = file.getOriginalFilename();
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String uniqueName = java.util.UUID.randomUUID().toString() + extension;
            String uploadUrl = supabaseUrl + "/storage/v1/object/productos/" + uniqueName;

            byte[] bytes = file.getBytes();
            String contentType = file.getContentType();
            if (contentType == null || contentType.isEmpty()) {
                contentType = "image/jpeg";
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
                throw new RuntimeException("Respuesta fallida de Supabase Storage (código " + response.statusCode() + "): " + response.body());
            }

            return supabaseUrl + "/storage/v1/object/public/productos/" + uniqueName;
        } catch (Exception e) {
            throw new RuntimeException("Fallo al subir la imagen del banner a Supabase Storage: " + e.getMessage(), e);
        }
    }
}