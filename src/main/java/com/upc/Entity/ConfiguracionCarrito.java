package com.upc.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "configuracion_carrito")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracionCarrito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "whatsapp_url", length = 500)
    private String whatsappUrl;

    @Column(name = "beneficio_1")
    private String beneficio1;

    @Column(name = "beneficio_2")
    private String beneficio2;

    @Column(name = "beneficio_3")
    private String beneficio3;

    @Column(name = "beneficio_4")
    private String beneficio4;
}
