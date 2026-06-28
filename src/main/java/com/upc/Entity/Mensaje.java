package com.upc.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "mensajes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Mensaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    private Chat chat;

    @Column(name = "contenido", columnDefinition = "TEXT")
    private String contenido;

    private String remitente; // "CLIENTE" o "ADMINISTRADOR"

    @Column(name = "fecha_envio")
    private LocalDateTime fechaEnvio;

    @Column(name = "wamid")
    private String wamid;
}

