package com.upc.Service;

import com.upc.Entity.Producto;
import com.upc.Entity.ShopVideo;
import com.upc.Repository.ProductoRepository;
import com.upc.Repository.ShopVideoRepository;
import com.upc.DTO.ShopVideoDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
public class ShopVideoServiceImpl implements ShopVideoService {

    @Autowired
    private ShopVideoRepository shopVideoRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Override
    @Transactional
    public ShopVideoDTO crearVideo(ShopVideoDTO dto) {
        ShopVideo video = new ShopVideo();
        video.setTitulo(dto.getTitulo());
        video.setDescripcion(dto.getDescripcion());
        video.setPlataforma(dto.getPlataforma());
        video.setVideoUrl(dto.getVideoUrl());
        video.setMiniaturaUrl(dto.getMiniaturaUrl() != null ? dto.getMiniaturaUrl() : "");
        video.setViews(dto.getViews() != null ? dto.getViews() : "0");
        video.setLikes(dto.getLikes() != null ? dto.getLikes() : "0");
        video.setClicks(dto.getClicks() != null ? dto.getClicks() : "0");
        video.setVisible(dto.getVisible() != null ? dto.getVisible() : true);

        if (dto.getProductosIds() != null && !dto.getProductosIds().isEmpty()) {
            List<Producto> products = productoRepository.findAllById(dto.getProductosIds());
            video.setProductos(products);
        } else {
            video.setProductos(new ArrayList<>());
        }

        ShopVideo saved = shopVideoRepository.save(video);
        return mapToDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShopVideoDTO> obtenerTodos() {
        return shopVideoRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShopVideoDTO> obtenerPublicos() {
        return shopVideoRepository.findAll().stream()
                .filter(v -> v.getVisible() != null && v.getVisible())
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ShopVideoDTO obtenerPorId(Long id) {
        ShopVideo video = shopVideoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Shop Video no encontrado con ID: " + id));
        return mapToDTO(video);
    }

    @Override
    @Transactional
    public void eliminarVideo(Long id) {
        if (!shopVideoRepository.existsById(id)) {
            throw new RuntimeException("Shop Video no encontrado con ID: " + id);
        }
        shopVideoRepository.deleteById(id);
    }

    @Override
    @Transactional
    public ShopVideoDTO actualizarVisibilidad(Long id, Boolean visible) {
        ShopVideo video = shopVideoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Shop Video no encontrado con ID: " + id));
        video.setVisible(visible);
        ShopVideo saved = shopVideoRepository.save(video);
        return mapToDTO(saved);
    }

    @Override
    @Transactional
    public ShopVideoDTO actualizarVideo(Long id, ShopVideoDTO dto) {
        ShopVideo video = shopVideoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Shop Video no encontrado con ID: " + id));
        video.setTitulo(dto.getTitulo());
        video.setDescripcion(dto.getDescripcion());
        video.setPlataforma(dto.getPlataforma());
        video.setVideoUrl(dto.getVideoUrl());
        if (dto.getMiniaturaUrl() != null) {
            video.setMiniaturaUrl(dto.getMiniaturaUrl());
        }
        if (dto.getViews() != null) {
            video.setViews(dto.getViews());
        }
        if (dto.getLikes() != null) {
            video.setLikes(dto.getLikes());
        }
        if (dto.getClicks() != null) {
            video.setClicks(dto.getClicks());
        }
        if (dto.getVisible() != null) {
            video.setVisible(dto.getVisible());
        }

        if (dto.getProductosIds() != null) {
            List<Producto> products = productoRepository.findAllById(dto.getProductosIds());
            video.setProductos(products);
        }

        ShopVideo saved = shopVideoRepository.save(video);
        return mapToDTO(saved);
    }

    @Override
    @Transactional
    public void incrementarClicks(Long id) {
        ShopVideo video = shopVideoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Shop Video no encontrado con ID: " + id));
        try {
            int current = Integer.parseInt(video.getClicks().replaceAll("[^0-9]", ""));
            video.setClicks(String.valueOf(current + 1));
        } catch (Exception e) {
            video.setClicks("1");
        }
        shopVideoRepository.save(video);
    }

    private ShopVideoDTO mapToDTO(ShopVideo video) {
        ShopVideoDTO dto = new ShopVideoDTO();
        dto.setId(video.getId());
        dto.setTitulo(video.getTitulo());
        dto.setDescripcion(video.getDescripcion());
        dto.setPlataforma(video.getPlataforma());
        dto.setVideoUrl(video.getVideoUrl());
        dto.setMiniaturaUrl(video.getMiniaturaUrl());
        dto.setViews(video.getViews());
        dto.setLikes(video.getLikes());
        dto.setClicks(video.getClicks());
        dto.setVisible(video.getVisible());

        if (video.getProductos() != null) {
            dto.setProductosIds(video.getProductos().stream()
                    .map(p -> p.getId())
                    .collect(Collectors.toList()));
        } else {
            dto.setProductosIds(new ArrayList<>());
        }
        return dto;
    }
}
