package com.upc.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductoDTO {

    private Long id;
    private String nombre;
    private String descripcion;
    private String descripcionCorta; // Alias for descripcion
    private Double precio; // Active price (offer price if discount is active)
    private Double precioOferta; // Alias for active price
    private Double precioOriginal; // Alias for precioAnterior (before discount)
    private Integer stock;
    private Integer stockInicial; // Alias for stock
    private String categoria;
    private String imageUrl;
    private Boolean activo;
    private Boolean visible;
    private Boolean esVisible;
    private Boolean destacado;
    private Boolean esDestacado;
    private Boolean nuevo;
    private Boolean esNuevo;

    // Technical sheet / extended specifications fields
    private String subtitulo;
    private Double precioAnterior;
    private Double rating;
    private Integer resenas;
    private String capacidad;
    private String material;
    private String aptoPara;
    private String acabado;
    private String diseno;
    private String garantia;
    private String incluye;
    private List<String> galeriaUrls;
    private List<String> tags;

    // Fields for the 4 tabs of "Agregar nuevo producto"
    private String imagenPrincipal;
    private List<String> miniaturasAdicionales;
    private String descripcionDetallada;
    private String caracteristicasDestacadas;

    // List of suggested/related product IDs
    private List<Long> sugeridosIds;
}
