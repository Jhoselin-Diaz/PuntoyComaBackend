package com.upc.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;

    @Column(unique = true, nullable = false)
    private String email;

    private String password;
    private String telefono;
    private String rol;
    private Boolean activo;

    // Security audit fields
    @Column(name = "intentos_fallidos")
    private Integer intentosFallidos;

    @Column(name = "cuenta_bloqueada")
    private Boolean cuentaBloqueada;

    @Column(name = "ultimo_acceso")
    private LocalDateTime ultimoAcceso;

    private Boolean habilitado;
}
