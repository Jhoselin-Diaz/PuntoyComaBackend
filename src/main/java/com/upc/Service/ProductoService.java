package com.upc.Service;

import com.upc.DTO.ProductoDTO;
import java.util.List;

public interface ProductoService {
    ProductoDTO crearProducto(ProductoDTO productoDTO);
    ProductoDTO agregarStockProducto(Long id, Integer cantidad, String proveedor, String notas);
    List<ProductoDTO> obtenerTodos();
    ProductoDTO obtenerPorId(Long id);
}
