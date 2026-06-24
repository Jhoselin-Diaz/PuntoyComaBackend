package com.upc.Service;

import com.upc.Entity.Producto;
import com.upc.Entity.InventarioMovimiento;
import com.upc.Repository.ProductoRepository;
import com.upc.Repository.InventarioMovimientoRepository;
import com.upc.DTO.ProductoDTO;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductoServiceImpl implements ProductoService {

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private InventarioMovimientoRepository inventarioMovimientoRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Override
    @Transactional
    public ProductoDTO crearProducto(ProductoDTO productoDTO) {
        Producto producto = modelMapper.map(productoDTO, Producto.class);
        
        // Associate suggest IDs if they exist in DB
        if (productoDTO.getSugeridosIds() != null && !productoDTO.getSugeridosIds().isEmpty()) {
            List<Producto> sugeridos = productoRepository.findAllById(productoDTO.getSugeridosIds());
            producto.setProductosSugeridos(sugeridos);
        } else {
            producto.setProductosSugeridos(new java.util.ArrayList<>());
        }
        
        Producto guardado = productoRepository.save(producto);
        return convertToDto(guardado);
    }

    @Override
    @Transactional
    public ProductoDTO agregarStockProducto(Long id, Integer cantidad, String proveedor, String notas) {
        Producto producto = productoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Producto no encontrado con el ID: " + id));

        // Update stock
        int nuevoStock = (producto.getStock() != null ? producto.getStock() : 0) + cantidad;
        producto.setStock(nuevoStock);
        Producto productoActualizado = productoRepository.save(producto);

        // Create historical movement record
        InventarioMovimiento movimiento = new InventarioMovimiento();
        movimiento.setCantidad(cantidad);
        movimiento.setTipoMovimiento("INGRESO");
        movimiento.setProveedor(proveedor);
        movimiento.setFecha(LocalDateTime.now());
        movimiento.setNotas(notas);
        movimiento.setProducto(productoActualizado);
        inventarioMovimientoRepository.save(movimiento);

        return convertToDto(productoActualizado);
    }

    @Override
    public List<ProductoDTO> obtenerTodos() {
        return productoRepository.findAll().stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }

    @Override
    public ProductoDTO obtenerPorId(Long id) {
        Producto producto = productoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Producto no encontrado con el ID: " + id));
        return convertToDto(producto);
    }

    private ProductoDTO convertToDto(Producto producto) {
        ProductoDTO dto = modelMapper.map(producto, ProductoDTO.class);
        if (producto.getProductosSugeridos() != null) {
            List<Long> ids = producto.getProductosSugeridos().stream()
                .map(Producto::getId)
                .collect(Collectors.toList());
            dto.setSugeridosIds(ids);
        } else {
            dto.setSugeridosIds(new java.util.ArrayList<>());
        }
        return dto;
    }
}
