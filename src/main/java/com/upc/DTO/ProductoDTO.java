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
    private Double precio;
    private Integer stock;
    private String categoria;
    private String imageUrl;
    private Boolean activo;

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
    private String miniaturasAdicionales;
    private String descripcionDetallada;
    private String caracteristicasDestacadas;

    // List of suggested/related product IDs
    private List<Long> sugeridosIds;
}
