package com.upc.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "productos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    private Double precio;
    private Integer stock;
    private String categoria;

    @Column(name = "image_url")
    private String imageUrl;

    private Boolean activo;

    // Technical sheet / extended specifications fields
    private String subtitulo;

    @Column(name = "precio_anterior")
    private Double precioAnterior;

    private Double rating;
    private Integer resenas;
    private String capacidad;
    private String material;

    @Column(name = "apto_para")
    private String aptoPara;

    private String acabado;
    private String diseno;
    private String garantia;
    private String incluye;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "producto_galeria", joinColumns = @JoinColumn(name = "producto_id"))
    @Column(name = "image_url")
    private List<String> galeriaUrls = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "producto_tags", joinColumns = @JoinColumn(name = "producto_id"))
    @Column(name = "tag")
    private List<String> tags = new ArrayList<>();

    // Fields for the 4 tabs of "Agregar nuevo producto"
    @Column(name = "imagen_principal", columnDefinition = "TEXT")
    private String imagenPrincipal;

    @Column(name = "miniaturas_adicionales", columnDefinition = "TEXT")
    private String miniaturasAdicionales;

    @Column(name = "descripcion_detallada", columnDefinition = "TEXT")
    private String descripcionDetallada;

    @Column(name = "caracteristicas_destacadas", columnDefinition = "TEXT")
    private String caracteristicasDestacadas;

    // Self-referencing ManyToMany relationship for recommended/related products
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "producto_sugeridos",
        joinColumns = @JoinColumn(name = "producto_id"),
        inverseJoinColumns = @JoinColumn(name = "sugerido_id")
    )
    private List<Producto> productosSugeridos = new ArrayList<>();
}
