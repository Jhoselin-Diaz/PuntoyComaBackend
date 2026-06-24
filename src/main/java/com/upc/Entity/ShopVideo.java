package com.upc.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Entity
@Table(name = "shop_videos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String titulo;
    private String plataforma;

    @Column(name = "video_url")
    private String videoUrl;

    @Column(name = "miniatura_url")
    private String miniaturaUrl;

    private String views;
    private String likes;
    private String clicks;
    private Boolean visible;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "shop_video_productos",
        joinColumns = @JoinColumn(name = "shop_video_id"),
        inverseJoinColumns = @JoinColumn(name = "producto_id")
    )
    private List<Producto> productos;
}
