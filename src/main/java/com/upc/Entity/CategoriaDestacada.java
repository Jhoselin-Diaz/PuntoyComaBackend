package com.upc.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "categorias_destacadas")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoriaDestacada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_categoria", nullable = false)
    private String nombreCategoria;

    @Column(nullable = false)
    private String tipo; // "MANUAL" o "AUTOMATICA_MAS_VISTOS"

    private Integer prioridad = 0;
    private Boolean visible = true;

    @Column(name = "imagen_url", length = 1024)
    private String imagenUrl;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "categoria_destacada_productos",
        joinColumns = @JoinColumn(name = "categoria_destacada_id"),
        inverseJoinColumns = @JoinColumn(name = "producto_id")
    )
    private List<Producto> productos = new ArrayList<>();
}
