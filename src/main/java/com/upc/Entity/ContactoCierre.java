package com.upc.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "contacto_cierre")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContactoCierre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "btn_text")
    private String btnText;

    private String number;

    @Column(columnDefinition = "TEXT")
    private String message;

    private Boolean visible;
}
