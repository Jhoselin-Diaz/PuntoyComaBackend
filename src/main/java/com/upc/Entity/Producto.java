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
    private Boolean visible;
    private Boolean destacado;
    private Boolean nuevo;

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

    @Column(name = "material_intro", columnDefinition = "TEXT")
    private String materialIntro;

    @Column(name = "material_cuidados", columnDefinition = "TEXT")
    private String materialCuidados;

    @Column(name = "envios_info", columnDefinition = "TEXT")
    private String enviosInfo;

    @Column(name = "devoluciones_info", columnDefinition = "TEXT")
    private String devolucionesInfo;

    @Column(name = "material_caracteristicas", columnDefinition = "TEXT")
    private String materialCaracteristicas;

    @Column(name = "apto_microondas")
    private Boolean aptoMicroondas;

    @Column(name = "apto_lavavajillas")
    private Boolean aptoLavavajillas;

    @Column(name = "resiste_choque_termico")
    private Boolean resisteChoqueTermico;

    @Column(name = "limpieza_suave")
    private Boolean limpiezaSuave;

    @Column(name = "prohibido_fuego_directo")
    private Boolean prohibidoFuegoDirecto;

    @Column(name = "apto_temperaturas")
    private Boolean aptoTemperaturas;

    @Column(name = "grado_alimentario")
    private Boolean gradoAlimentario;

    @Column(name = "evitar_abrasivos")
    private Boolean evitarAbrasivos;

    @Column(name = "control_humedad")
    private Boolean controlHumedad;

    @Column(name = "lavado_mano")
    private Boolean lavadoMano;

    @Column(name = "vistas_contador")
    private Integer vistasContador = 0;

    @Column(name = "fecha_inicio_oferta")
    private java.time.LocalDate fechaInicioOferta;

    @Column(name = "fecha_fin_oferta")
    private java.time.LocalDate fechaFinOferta;

    // Self-referencing ManyToMany relationship for recommended/related products
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "producto_sugeridos",
        joinColumns = @JoinColumn(name = "producto_id"),
        inverseJoinColumns = @JoinColumn(name = "sugerido_id")
    )
    private List<Producto> productosSugeridos = new ArrayList<>();
}
