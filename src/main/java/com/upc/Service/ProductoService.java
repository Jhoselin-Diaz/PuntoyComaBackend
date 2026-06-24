package com.upc.Service;

import com.upc.DTO.ProductoDTO;
import java.util.List;

public interface ProductoService {
    ProductoDTO crearProducto(ProductoDTO productoDTO);
    ProductoDTO crearProductoConImagen(ProductoDTO productoDTO, org.springframework.web.multipart.MultipartFile imagen);
    ProductoDTO agregarStockProducto(Long id, Integer cantidad, String proveedor, String notas);
    List<ProductoDTO> obtenerTodos();
    List<ProductoDTO> obtenerPublicos();
    ProductoDTO obtenerPorId(Long id);
    void eliminarProducto(Long id);
}
