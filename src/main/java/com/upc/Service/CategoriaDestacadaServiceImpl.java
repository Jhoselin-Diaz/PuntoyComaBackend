package com.upc.Service;

import com.upc.DTO.CategoriaDestacadaDTO;
import com.upc.DTO.ProductoDTO;
import com.upc.Entity.CategoriaDestacada;
import com.upc.Entity.Producto;
import com.upc.Repository.CategoriaDestacadaRepository;
import com.upc.Repository.ProductoRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CategoriaDestacadaServiceImpl implements CategoriaDestacadaService {

    @Autowired
    private CategoriaDestacadaRepository categoriaRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Override
    @Transactional
    public CategoriaDestacadaDTO crearCategoria(CategoriaDestacadaDTO dto) {
        CategoriaDestacada categoria = new CategoriaDestacada();
        categoria.setNombreCategoria(dto.getNombreCategoria());
        categoria.setTipo(dto.getTipo());
        categoria.setPrioridad(dto.getPrioridad() != null ? dto.getPrioridad() : 0);
        categoria.setVisible(dto.getVisible() != null ? dto.getVisible() : true);
        categoria.setImagenUrl(dto.getImagenUrl());

        if ("MANUAL".equalsIgnoreCase(dto.getTipo()) && dto.getProductosIds() != null && !dto.getProductosIds().isEmpty()) {
            List<Producto> productos = productoRepository.findAllById(dto.getProductosIds());
            categoria.setProductos(productos);
        } else {
            categoria.setProductos(new ArrayList<>());
        }

        CategoriaDestacada guardado = categoriaRepository.save(categoria);
        return convertToDto(guardado);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoriaDestacadaDTO> obtenerTodas() {
        return categoriaRepository.findAll().stream()
            .map(this::convertToDto)
            .sorted((c1, c2) -> {
                int p1 = c1.getPrioridad() != null ? c1.getPrioridad() : 0;
                int p2 = c2.getPrioridad() != null ? c2.getPrioridad() : 0;
                return Integer.compare(p1, p2);
            })
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public CategoriaDestacadaDTO obtenerPorId(Long id) {
        CategoriaDestacada categoria = categoriaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Categoría destacada no encontrada con ID: " + id));
        return convertToDto(categoria);
    }

    @Override
    @Transactional
    public CategoriaDestacadaDTO actualizarCategoria(Long id, CategoriaDestacadaDTO dto) {
        CategoriaDestacada categoria = categoriaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Categoría destacada no encontrada con ID: " + id));

        categoria.setNombreCategoria(dto.getNombreCategoria());
        categoria.setTipo(dto.getTipo());
        categoria.setPrioridad(dto.getPrioridad() != null ? dto.getPrioridad() : 0);
        categoria.setVisible(dto.getVisible() != null ? dto.getVisible() : true);
        categoria.setImagenUrl(dto.getImagenUrl());

        if ("MANUAL".equalsIgnoreCase(dto.getTipo())) {
            if (dto.getProductosIds() != null) {
                List<Producto> productos = productoRepository.findAllById(dto.getProductosIds());
                categoria.setProductos(productos);
            } else {
                categoria.setProductos(new ArrayList<>());
            }
        } else {
            categoria.setProductos(new ArrayList<>());
        }

        CategoriaDestacada guardado = categoriaRepository.save(categoria);
        return convertToDto(guardado);
    }

    @Override
    @Transactional
    public void eliminarCategoria(Long id) {
        if (!categoriaRepository.existsById(id)) {
            throw new RuntimeException("Categoría destacada no encontrada con ID: " + id);
        }
        categoriaRepository.deleteById(id);
    }

    @Override
    @Transactional
    public CategoriaDestacadaDTO actualizarVisibilidad(Long id, Boolean visible) {
        CategoriaDestacada categoria = categoriaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Categoría destacada no encontrada con ID: " + id));
        categoria.setVisible(visible);
        CategoriaDestacada guardado = categoriaRepository.save(categoria);
        return convertToDto(guardado);
    }

    private CategoriaDestacadaDTO convertToDto(CategoriaDestacada cat) {
        CategoriaDestacadaDTO dto = new CategoriaDestacadaDTO();
        dto.setId(cat.getId());
        dto.setNombreCategoria(cat.getNombreCategoria());
        dto.setTipo(cat.getTipo());
        dto.setPrioridad(cat.getPrioridad());
        dto.setVisible(cat.getVisible());
        dto.setImagenUrl(cat.getImagenUrl());

        if ("AUTOMATICA_MAS_VISTOS".equalsIgnoreCase(cat.getTipo())) {
            List<Producto> masVistos = productoRepository.findTop5MasVistos();
            dto.setProductos(masVistos.stream().map(this::convertProductoToDto).collect(Collectors.toList()));
            dto.setProductosCount(masVistos.size());
            dto.setProductosIds(masVistos.stream().map(Producto::getId).collect(Collectors.toList()));
        } else {
            if (cat.getProductos() != null) {
                dto.setProductos(cat.getProductos().stream().map(this::convertProductoToDto).collect(Collectors.toList()));
                dto.setProductosCount(cat.getProductos().size());
                dto.setProductosIds(cat.getProductos().stream().map(Producto::getId).collect(Collectors.toList()));
            } else {
                dto.setProductos(new ArrayList<>());
                dto.setProductosCount(0);
                dto.setProductosIds(new ArrayList<>());
            }
        }
        return dto;
    }

    private ProductoDTO convertProductoToDto(Producto p) {
        ProductoDTO dto = modelMapper.map(p, ProductoDTO.class);
        dto.setDescripcionCorta(p.getDescripcion());
        dto.setPrecioOferta(p.getPrecio());
        dto.setPrecioOriginal(p.getPrecioAnterior());
        dto.setStockInicial(p.getStock());
        dto.setEsVisible(p.getVisible());
        dto.setVisible(p.getVisible());
        dto.setActivo(p.getActivo());
        dto.setEsDestacado(p.getDestacado());
        dto.setDestacado(p.getDestacado());
        dto.setEsNuevo(p.getNuevo());
        dto.setNuevo(p.getNuevo());
        dto.setVistasContador(p.getVistasContador() != null ? p.getVistasContador() : 0);
        return dto;
    }
}
