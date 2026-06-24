package com.upc.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "configuraciones_negocio")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracionNegocio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Contact/business information
    @Column(name = "nombre_tienda")
    private String nombreTienda;

    private String correo;
    private String telefono;
    private String whatsapp;
    private String instagram;
    private String direccion;

    // Appearance variables
    @Column(name = "color_principal")
    private String colorPrincipal;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "portada_url")
    private String portadaUrl;

    // Automation flags
    @Column(name = "sugerir_acciones")
    private Boolean sugerirAcciones;

    @Column(name = "validar_pagos")
    private Boolean validarPagos;

    @Column(name = "detectar_incompletos")
    private Boolean detectarIncompletos;

    @Column(name = "clasificar_conversaciones")
    private Boolean clasificarConversaciones;

    // CMS Home banner variables
    @Column(name = "banner_title")
    private String bannerTitle;

    @Column(name = "banner_subtitle")
    private String bannerSubtitle;

    @Column(name = "banner_btn_text")
    private String bannerBtnText;

    @Column(name = "banner_btn_link")
    private String bannerBtnLink;

    @Column(name = "banner_image")
    private String bannerImage;

    @Column(name = "banner_visible")
    private Boolean bannerVisible;
}
