package com.upc.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "cupones")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Cupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo", unique = true, nullable = false)
    private String codigo;

    @Column(name = "porcentaje_descuento", nullable = false)
    private Integer porcentajeDescuento;

    @Column(name = "activo", nullable = false)
    private Boolean activo;

    @Column(name = "monto_minimo")
    private Double montoMinimo;

    @Column(name = "fecha_inicio")
    private java.time.LocalDate fechaInicio;

    @Column(name = "fecha_fin")
    private java.time.LocalDate fechaFin;

    @Column(name = "limite_uso")
    private Integer limiteUso;

    @Column(name = "usos_actuales")
    private Integer usosActuales = 0;
}
