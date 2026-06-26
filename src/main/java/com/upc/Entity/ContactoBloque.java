package com.upc.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "contacto_bloques")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContactoBloque {

    @Id
    private String id; // block-wa, block-ig, block-support, block-email, block-info

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String icon; // whatsapp, instagram, support, email, info

    @Column(name = "btn_text")
    private String btnText;

    @Column(name = "btn_link", columnDefinition = "TEXT")
    private String btnLink;

    private Boolean visible;
}
