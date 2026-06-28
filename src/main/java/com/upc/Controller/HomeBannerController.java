package com.upc.Controller;

import com.upc.DTO.HomeBannerDTO;
import com.upc.Service.HomeBannerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/content/home-banner")
@CrossOrigin(origins = "http://localhost:4200")
public class HomeBannerController {

    @Autowired
    private HomeBannerService homeBannerService;

    @GetMapping
    public ResponseEntity<HomeBannerDTO> obtenerBanner() {
        HomeBannerDTO banner = homeBannerService.obtenerBanner();
        return new ResponseEntity<>(banner, HttpStatus.OK);
    }

    /**
     * Endpoint para actualización en formato JSON plano (usado al guardar URLs de Google Drive o sin cambiar imagen)
     */
    @PutMapping
    public ResponseEntity<HomeBannerDTO> actualizarBannerJson(@RequestBody HomeBannerDTO bannerDTO) {
        HomeBannerDTO actualizado = homeBannerService.actualizarBanner(bannerDTO, null);
        return new ResponseEntity<>(actualizado, HttpStatus.OK);
    }

    /**
     * Endpoint para actualización en formato Multipart (usado al subir una imagen física local a Supabase)
     */
    @PutMapping(consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<HomeBannerDTO> actualizarBannerMultipart(
            @RequestPart("banner") String bannerJson,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.findAndRegisterModules();
            HomeBannerDTO bannerDTO = objectMapper.readValue(bannerJson, HomeBannerDTO.class);
            
            HomeBannerDTO actualizado = homeBannerService.actualizarBanner(bannerDTO, file);
            return new ResponseEntity<>(actualizado, HttpStatus.OK);
        } catch (Exception e) {
            throw new RuntimeException("Error al procesar la actualización multipart del banner: " + e.getMessage(), e);
        }
    }
}