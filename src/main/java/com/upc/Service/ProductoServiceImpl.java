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
        
        // Handle alias/alternative field mapping
        if (producto.getDescripcion() == null || producto.getDescripcion().isEmpty()) {
            if (productoDTO.getDescripcionCorta() != null) {
                producto.setDescripcion(productoDTO.getDescripcionCorta());
            }
        }
        
        if (productoDTO.getPrecioOriginal() != null) {
            producto.setPrecioAnterior(productoDTO.getPrecioOriginal());
        }
        
        if (productoDTO.getPrecioOferta() != null) {
            producto.setPrecio(productoDTO.getPrecioOferta());
        } else if (productoDTO.getPrecio() != null) {
            producto.setPrecio(productoDTO.getPrecio());
        }
        
        if (productoDTO.getStockInicial() != null) {
            producto.setStock(productoDTO.getStockInicial());
        } else if (productoDTO.getStock() != null) {
            producto.setStock(productoDTO.getStock());
        }
        
        if (productoDTO.getImagenPrincipal() != null && !productoDTO.getImagenPrincipal().isEmpty()) {
            producto.setImagenPrincipal(productoDTO.getImagenPrincipal());
            producto.setImageUrl(productoDTO.getImagenPrincipal());
        } else if (productoDTO.getImageUrl() != null && !productoDTO.getImageUrl().isEmpty()) {
            producto.setImageUrl(productoDTO.getImageUrl());
            producto.setImagenPrincipal(productoDTO.getImageUrl());
        }
        
        Boolean isVis = true;
        if (productoDTO.getEsVisible() != null) {
            isVis = productoDTO.getEsVisible();
        } else if (productoDTO.getVisible() != null) {
            isVis = productoDTO.getVisible();
        } else if (productoDTO.getActivo() != null) {
            isVis = productoDTO.getActivo();
        }
        producto.setActivo(isVis);
        producto.setVisible(isVis);
        
        Boolean isDest = false;
        if (productoDTO.getEsDestacado() != null) {
            isDest = productoDTO.getEsDestacado();
        } else if (productoDTO.getDestacado() != null) {
            isDest = productoDTO.getDestacado();
        }
        producto.setDestacado(isDest);
        
        Boolean isNew = false;
        if (productoDTO.getEsNuevo() != null) {
            isNew = productoDTO.getEsNuevo();
        } else if (productoDTO.getNuevo() != null) {
            isNew = productoDTO.getNuevo();
        }
        producto.setNuevo(isNew);
        
        if (productoDTO.getDescripcionDetallada() != null) {
            producto.setDescripcionDetallada(productoDTO.getDescripcionDetallada());
        }
        
        if (productoDTO.getCaracteristicasDestacadas() != null) {
            producto.setCaracteristicasDestacadas(productoDTO.getCaracteristicasDestacadas());
        }
        
        // Miniatures / Gallery mapping
        List<String> gallery = new java.util.ArrayList<>();
        if (productoDTO.getGaleriaUrls() != null && !productoDTO.getGaleriaUrls().isEmpty()) {
            gallery.addAll(productoDTO.getGaleriaUrls());
        }
        if (productoDTO.getMiniaturasAdicionales() != null && !productoDTO.getMiniaturasAdicionales().isEmpty()) {
            for (String url : productoDTO.getMiniaturasAdicionales()) {
                if (url != null && !url.trim().isEmpty() && !gallery.contains(url.trim())) {
                    gallery.add(url.trim());
                }
            }
        }
        producto.setGaleriaUrls(gallery);
        
        // Also save to miniaturasAdicionales string in the entity for backward compatibility
        if (!gallery.isEmpty()) {
            producto.setMiniaturasAdicionales(String.join(",", gallery));
        } else {
            producto.setMiniaturasAdicionales(null);
        }
        
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
    public List<ProductoDTO> obtenerPublicos() {
        return productoRepository.findByVisibleTrue().stream()
            .map(this::convertToDto)
            .collect(Collectors.toList());
    }

    @Override
    public ProductoDTO obtenerPorId(Long id) {
        Producto producto = productoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Producto no encontrado con el ID: " + id));
        return convertToDto(producto);
    }

    @org.springframework.beans.factory.annotation.Value("${supabase.url}")
    private String supabaseUrl;

    @org.springframework.beans.factory.annotation.Value("${supabase.key:}")
    private String supabaseKey;

    @Override
    @Transactional
    public ProductoDTO crearProductoConImagen(ProductoDTO productoDTO, org.springframework.web.multipart.MultipartFile imagen) {
        if (imagen == null || imagen.isEmpty()) {
            throw new RuntimeException("La imagen principal es requerida y no puede estar vacía.");
        }

        // Upload to Supabase Storage
        String publicUrl = subirImagenASupabase(imagen);

        // Update URLs in DTO
        productoDTO.setImagenPrincipal(publicUrl);
        productoDTO.setImageUrl(publicUrl);

        // Save using our existing robust logic
        return crearProducto(productoDTO);
    }

    private String subirImagenASupabase(org.springframework.web.multipart.MultipartFile file) {
        try {
            String extension = ".jpg";
            String originalFilename = file.getOriginalFilename();
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String uniqueName = java.util.UUID.randomUUID().toString() + extension;
            String uploadUrl = supabaseUrl + "/storage/v1/object/productos/" + uniqueName;

            byte[] bytes = file.getBytes();
            String contentType = file.getContentType();
            if (contentType == null || contentType.isEmpty()) {
                contentType = "image/jpeg";
            }

            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(uploadUrl))
                    .header("apikey", supabaseKey)
                    .header("Authorization", "Bearer " + supabaseKey)
                    .header("Content-Type", contentType)
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(bytes))
                    .build();

            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("Respuesta fallida de Supabase Storage (código " + response.statusCode() + "): " + response.body());
            }

            return supabaseUrl + "/storage/v1/object/public/productos/" + uniqueName;
        } catch (Exception e) {
            throw new RuntimeException("Fallo en la carga de la imagen a Supabase Storage: " + e.getMessage(), e);
        }
    }

    private ProductoDTO convertToDto(Producto producto) {
        ProductoDTO dto = modelMapper.map(producto, ProductoDTO.class);
        
        // Populate DTO aliases for robust frontend compatibility
        dto.setDescripcionCorta(producto.getDescripcion());
        dto.setPrecioOferta(producto.getPrecio());
        dto.setPrecioOriginal(producto.getPrecioAnterior());
        dto.setStockInicial(producto.getStock());
        dto.setEsVisible(producto.getVisible());
        dto.setVisible(producto.getVisible());
        dto.setActivo(producto.getActivo());
        dto.setEsDestacado(producto.getDestacado());
        dto.setDestacado(producto.getDestacado());
        dto.setEsNuevo(producto.getNuevo());
        dto.setNuevo(producto.getNuevo());
        
        // Map both gallery variables to the same list for frontend ease of use
        if (producto.getGaleriaUrls() != null) {
            dto.setGaleriaUrls(new java.util.ArrayList<>(producto.getGaleriaUrls()));
            dto.setMiniaturasAdicionales(new java.util.ArrayList<>(producto.getGaleriaUrls()));
        } else {
            dto.setGaleriaUrls(new java.util.ArrayList<>());
            dto.setMiniaturasAdicionales(new java.util.ArrayList<>());
        }
        
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

    @Override
    @Transactional
    public void eliminarProducto(Long id) {
        Producto producto = productoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Producto no encontrado con el ID: " + id));

        // Delete movements associated with this product
        List<InventarioMovimiento> movements = inventarioMovimientoRepository.findAll();
        for (InventarioMovimiento movement : movements) {
            if (movement.getProducto() != null && movement.getProducto().getId().equals(id)) {
                inventarioMovimientoRepository.delete(movement);
            }
        }

        // Remove from sugeridos of other products (if any)
        List<Producto> referencingProducts = productoRepository.findAll();
        for (Producto other : referencingProducts) {
            if (other.getProductosSugeridos() != null && other.getProductosSugeridos().contains(producto)) {
                other.getProductosSugeridos().remove(producto);
                productoRepository.save(other);
            }
        }

        // Clear this product's own sugeridos to drop references in junction table
        producto.getProductosSugeridos().clear();
        productoRepository.save(producto);

        productoRepository.delete(producto);
    }
}
