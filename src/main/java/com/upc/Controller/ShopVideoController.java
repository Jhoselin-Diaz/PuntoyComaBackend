package com.upc.Controller;

import com.upc.DTO.ShopVideoDTO;
import com.upc.Service.ShopVideoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping({"/api/v1/videos", "/api/videos"})
@CrossOrigin(origins = "http://localhost:4200")
public class ShopVideoController {

    @Autowired
    private ShopVideoService shopVideoService;

    @PostMapping
    public ResponseEntity<ShopVideoDTO> crearVideo(@RequestBody ShopVideoDTO shopVideoDTO) {
        ShopVideoDTO creado = shopVideoService.crearVideo(shopVideoDTO);
        return new ResponseEntity<>(creado, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<ShopVideoDTO>> obtenerTodos() {
        return new ResponseEntity<>(shopVideoService.obtenerTodos(), HttpStatus.OK);
    }

    @GetMapping("/publicos")
    public ResponseEntity<List<ShopVideoDTO>> obtenerPublicos() {
        return new ResponseEntity<>(shopVideoService.obtenerPublicos(), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShopVideoDTO> obtenerPorId(@PathVariable Long id) {
        return new ResponseEntity<>(shopVideoService.obtenerPorId(id), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarVideo(@PathVariable Long id) {
        shopVideoService.eliminarVideo(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @CrossOrigin(origins = "http://localhost:4200")
    @PatchMapping("/{id}/visibilidad")
    public ResponseEntity<ShopVideoDTO> actualizarVisibilidad(
            @PathVariable Long id,
            @RequestParam Boolean visible) {
        ShopVideoDTO actualizado = shopVideoService.actualizarVisibilidad(id, visible);
        return new ResponseEntity<>(actualizado, HttpStatus.OK);
    }

    @CrossOrigin(origins = "http://localhost:4200")
    @PutMapping("/{id}")
    public ResponseEntity<ShopVideoDTO> actualizarVideo(
            @PathVariable Long id,
            @RequestBody ShopVideoDTO shopVideoDTO) {
        ShopVideoDTO actualizado = shopVideoService.actualizarVideo(id, shopVideoDTO);
        return new ResponseEntity<>(actualizado, HttpStatus.OK);
    }

    @PostMapping("/{id}/click")
    public ResponseEntity<Void> registrarClick(@PathVariable Long id) {
        shopVideoService.incrementarClicks(id);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
