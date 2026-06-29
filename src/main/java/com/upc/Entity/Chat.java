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

    @Column(name = "telefono_cliente", unique = true, nullable = false)
    private String telefonoCliente;

    @Column(name = "nombre_cliente")
    private String nombreCliente;

    @Column(name = "ultimo_mensaje", columnDefinition = "TEXT")
    private String ultimoMensaje;

    @Column(name = "fecha_ultima_actualizacion")
    private LocalDateTime fechaUltimaActualizacion;

    @Column(name = "unread_count")
    private Integer unreadCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "prioridad")
    private String prioridad; // "ALTA", "INTERMEDIA", "BAJA"

    @Column(name = "sugerencia_ia", columnDefinition = "TEXT")
    private String sugerenciaIa;

    @Column(name = "pedido_referenciado_id")
    private Long pedidoReferenciadoId;

    @Column(name = "pedido_identificado")
    private Boolean pedidoIdentificado;

    @Column(name = "direccion_detectada")
    private Boolean direccionDetectada;

    @Column(name = "datos_completos")
    private Boolean datosCompletos;

    @Column(name = "fase_pedido")
    private String fasePedido; // "INICIAL", "CONCILIACION", "RECLAMO_CONSULTA"
}

