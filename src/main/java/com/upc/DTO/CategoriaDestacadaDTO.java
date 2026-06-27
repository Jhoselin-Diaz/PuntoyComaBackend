package com.upc.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoriaDestacadaDTO {
    private Long id;
    private String nombreCategoria;
    private String tipo; // "MANUAL" o "AUTOMATICA_MAS_VISTOS"
    private Integer prioridad;
    private Boolean visible;
    private String imagenUrl;
    private Integer productosCount;
    private List<Long> productosIds;
    private List<ProductoDTO> productos;
}
