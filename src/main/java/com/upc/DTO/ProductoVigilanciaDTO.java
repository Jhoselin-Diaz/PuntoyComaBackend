package com.upc.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoVigilanciaDTO {
    private Long id;
    private String nombre;
    private String sku;
    private Integer ventas;
    private Integer stock;
    private Integer disponible;
    private String tipo; // "venta", "alerta", "agotado"
}
