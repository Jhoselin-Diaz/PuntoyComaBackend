package com.upc.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HomeBannerDTO {
    private Long id;
    private String titulo;
    private String subtitulo;
    private String textoBoton;
    private String linkBoton;
    private String imagenUrl;
    private LocalDateTime actualizadoEn;
    private Boolean visible;
}
