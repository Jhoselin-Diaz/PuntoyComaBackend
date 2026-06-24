package com.upc.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "chats")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Chat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // e.g., 'NORMAL', 'EN_REVISION' for priority alerts
    private String estado;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = true)
    private Pedido pedido;

    // AI assistant status fields
    @Column(name = "pedido_identificado")
    private Boolean pedidoIdentificado;

    @Column(name = "direccion_detectada")
    private Boolean direccionDetectada;

    @Column(name = "datos_completos")
    private Boolean datosCompletos;

    @Column(name = "contexto_analizado")
    private Boolean contextoAnalizado;

    @Column(name = "mensaje_generado", columnDefinition = "TEXT")
    private String mensajeGenerado;
}
