package com.upc.Controller;

import com.upc.DTO.ProductoDTO;
import com.upc.Service.ProductoService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/productos")
@CrossOrigin("*")
public class ProductoController {

    @Autowired
    private ProductoService productoService;

    @PostMapping
    public ResponseEntity<ProductoDTO> crearProducto(@RequestBody ProductoDTO productoDTO) {
        ProductoDTO creado = productoService.crearProducto(productoDTO);
        return new ResponseEntity<>(creado, HttpStatus.CREATED);
    }

    @PatchMapping("/{id}/agregar-stock")
    public ResponseEntity<ProductoDTO> agregarStockProducto(
            @PathVariable Long id,
            @RequestBody StockRequest stockRequest) {
        
        ProductoDTO actualizado = productoService.agregarStockProducto(
                id,
                stockRequest.getCantidad(),
                stockRequest.getProveedor(),
                stockRequest.getNotas()
        );
        return new ResponseEntity<>(actualizado, HttpStatus.OK);
    }

    @GetMapping
    public ResponseEntity<List<ProductoDTO>> obtenerTodos() {
        return new ResponseEntity<>(productoService.obtenerTodos(), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductoDTO> obtenerPorId(@PathVariable Long id) {
        return new ResponseEntity<>(productoService.obtenerPorId(id), HttpStatus.OK);
    }

    @Data
    public static class StockRequest {
        private Integer cantidad;
        private String proveedor;
        private String notas;
    }
}
