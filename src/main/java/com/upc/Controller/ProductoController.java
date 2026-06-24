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
@RequestMapping({"/api/v1/productos", "/api/productos"})
@CrossOrigin(origins = "http://localhost:4200")
public class ProductoController {

    @Autowired
    private ProductoService productoService;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @PostMapping
    public ResponseEntity<ProductoDTO> crearProducto(@RequestBody ProductoDTO productoDTO) {
        ProductoDTO creado = productoService.crearProducto(productoDTO);
        return new ResponseEntity<>(creado, HttpStatus.CREATED);
    }

    @PostMapping(consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductoDTO> crearProductoConImagen(
            @RequestParam("imagen") org.springframework.web.multipart.MultipartFile imagen,
            @RequestParam("producto") String productoJson) {
        try {
            ProductoDTO productoDTO = objectMapper.readValue(productoJson, ProductoDTO.class);
            ProductoDTO creado = productoService.crearProductoConImagen(productoDTO, imagen);
            return new ResponseEntity<>(creado, HttpStatus.CREATED);
        } catch (Exception e) {
            throw new RuntimeException("Error al procesar la creación del producto con imagen: " + e.getMessage(), e);
        }
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

    @GetMapping("/publicos")
    public ResponseEntity<List<ProductoDTO>> obtenerPublicos() {
        return new ResponseEntity<>(productoService.obtenerPublicos(), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductoDTO> obtenerPorId(@PathVariable Long id) {
        return new ResponseEntity<>(productoService.obtenerPorId(id), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarProducto(@PathVariable Long id) {
        productoService.eliminarProducto(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Data
    public static class StockRequest {
        private Integer cantidad;
        private String proveedor;
        private String notas;
    }
}
