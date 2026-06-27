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

    @com.fasterxml.jackson.annotation.JsonProperty("material_intro")
    private String materialIntro;

    @com.fasterxml.jackson.annotation.JsonProperty("material_cuidados")
    private String materialCuidados;

    @com.fasterxml.jackson.annotation.JsonProperty("envios_info")
    private String enviosInfo;

    @com.fasterxml.jackson.annotation.JsonProperty("devoluciones_info")
    private String devolucionesInfo;

    @com.fasterxml.jackson.annotation.JsonProperty("material_caracteristicas")
    private String materialCaracteristicas;

    @com.fasterxml.jackson.annotation.JsonProperty("apto_microondas")
    private Boolean aptoMicroondas;

    @com.fasterxml.jackson.annotation.JsonProperty("apto_lavavajillas")
    private Boolean aptoLavavajillas;

    @com.fasterxml.jackson.annotation.JsonProperty("resiste_choque_termico")
    private Boolean resisteChoqueTermico;

    @com.fasterxml.jackson.annotation.JsonProperty("limpieza_suave")
    private Boolean limpiezaSuave;

    @com.fasterxml.jackson.annotation.JsonProperty("prohibido_fuego_directo")
    private Boolean prohibidoFuegoDirecto;

    @com.fasterxml.jackson.annotation.JsonProperty("apto_temperaturas")
    private Boolean aptoTemperaturas;

    @com.fasterxml.jackson.annotation.JsonProperty("grado_alimentario")
    private Boolean gradoAlimentario;

    @com.fasterxml.jackson.annotation.JsonProperty("evitar_abrasivos")
    private Boolean evitarAbrasivos;

    @com.fasterxml.jackson.annotation.JsonProperty("control_humedad")
    private Boolean controlHumedad;

    @com.fasterxml.jackson.annotation.JsonProperty("lavado_mano")
    private Boolean lavadoMano;

    @com.fasterxml.jackson.annotation.JsonProperty("vistas_contador")
    private Integer vistasContador = 0;

    private java.time.LocalDate fechaInicioOferta;
    private java.time.LocalDate fechaFinOferta;

    // List of suggested/related product IDs
    private List<Long> sugeridosIds;
}
