package com.upc.Controller;

import com.upc.DTO.ProductoDTO;
import com.upc.DTO.MovimientoInventarioDTO;
import com.upc.Service.ProductoService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping({"/api/v1/productos", "/api/productos", "/api/admin/productos", "/admin/productos"})
@CrossOrigin(origins = {"http://localhost:4200", "https://puntoycoma-store.netlify.app", "https://puntoycoma-store.netlify.app/"})
public class ProductoController {

    @Autowired
    private ProductoService productoService;

    @Autowired
    private com.upc.Repository.ProductoRepository productoRepository;

    @Autowired
    private org.modelmapper.ModelMapper modelMapper;

    @PostMapping(value = {"/{id}/imagen", "/{id}/image"}, consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> subirImagenProducto(
            @PathVariable Long id,
            @RequestParam(value = "file", required = false) org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "imagen", required = false) org.springframework.web.multipart.MultipartFile imagen) {

        org.springframework.web.multipart.MultipartFile archivo = (file != null && !file.isEmpty()) ? file : imagen;
        if (archivo == null || archivo.isEmpty()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "El archivo de imagen es requerido y no puede estar vacío."));
        }

        String originalFilename = archivo.getOriginalFilename();
        String extension = ".png";
        if (originalFilename != null && originalFilename.contains(".")) {
            String ext = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
            if (ext.matches("\\.(jpg|jpeg|png|webp|gif)")) {
                extension = ext;
            }
        }

        try {
            java.nio.file.Path uploadsDir = java.nio.file.Paths.get("uploads", "productos");
            if (!java.nio.file.Files.exists(uploadsDir)) {
                java.nio.file.Files.createDirectories(uploadsDir);
            }

            String filename = "prod_" + id + "_" + System.currentTimeMillis() + extension;
            java.nio.file.Path targetPath = uploadsDir.resolve(filename);
            java.nio.file.Files.copy(archivo.getInputStream(), targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            String publicUrl = "http://localhost:8080/uploads/productos/" + filename;

            com.upc.Entity.Producto producto = productoRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado con el ID: " + id));

            producto.setImageUrl(publicUrl);
            producto.setImagenPrincipal(publicUrl);
            com.upc.Entity.Producto guardado = productoRepository.save(producto);

            ProductoDTO dto = modelMapper.map(guardado, ProductoDTO.class);
            dto.setImageUrl(publicUrl);
            dto.setImagenPrincipal(publicUrl);

            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of("error", "Error al guardar la imagen: " + e.getMessage()));
        }
    }

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
                stockRequest.getNotas(),
                stockRequest.getTipoMovimiento()
        );
        return new ResponseEntity<>(actualizado, HttpStatus.OK);
    }

    @GetMapping
    public ResponseEntity<List<ProductoDTO>> obtenerTodos() {
        return new ResponseEntity<>(productoService.obtenerTodos(), HttpStatus.OK);
    }

    @GetMapping("/movimientos")
    public ResponseEntity<List<MovimientoInventarioDTO>> obtenerMovimientos() {
        return new ResponseEntity<>(productoService.obtenerMovimientos(), HttpStatus.OK);
    }

    @GetMapping("/publicos")
    public ResponseEntity<List<ProductoDTO>> obtenerPublicos() {
        return new ResponseEntity<>(productoService.obtenerPublicos(), HttpStatus.OK);
    }

    @GetMapping("/mas-vistos")
    public ResponseEntity<List<ProductoDTO>> obtenerMasVistos() {
        return new ResponseEntity<>(productoService.obtenerMasVistos(), HttpStatus.OK);
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

    @CrossOrigin(origins = {"http://localhost:4200", "https://puntoycoma-store.netlify.app", "https://puntoycoma-store.netlify.app/"})
    @PatchMapping("/{id}/visibilidad")
    public ResponseEntity<ProductoDTO> actualizarVisibilidad(
            @PathVariable Long id,
            @RequestParam Boolean visible) {
        ProductoDTO actualizado = productoService.actualizarVisibilidad(id, visible);
        return new ResponseEntity<>(actualizado, HttpStatus.OK);
    }

    @CrossOrigin(origins = {"http://localhost:4200", "https://puntoycoma-store.netlify.app", "https://puntoycoma-store.netlify.app/"})
    @PutMapping("/{id}")
    public ResponseEntity<ProductoDTO> actualizarProducto(
            @PathVariable Long id,
            @RequestBody ProductoDTO productoDTO) {
        ProductoDTO actualizado = productoService.actualizarProducto(id, productoDTO);
        return new ResponseEntity<>(actualizado, HttpStatus.OK);
    }

    @Data
    public static class StockRequest {
        private Integer cantidad;
        private String proveedor;
        private String notas;
        private String tipoMovimiento;
    }
}
