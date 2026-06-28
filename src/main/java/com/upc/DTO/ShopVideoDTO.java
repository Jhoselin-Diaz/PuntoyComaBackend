package com.upc.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopVideoDTO {
    private Long id;
    private String titulo;
    private String descripcion;
    private String plataforma;
    private String videoUrl;
    private String miniaturaUrl;
    private String views;
    private String likes;
    private String clicks;
    private Boolean visible;
    private List<Long> productosIds;
}
