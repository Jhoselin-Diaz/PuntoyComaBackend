package com.upc.Controller;

import com.upc.DTO.CategoriaDestacadaDTO;
import com.upc.Service.CategoriaDestacadaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping({"/api/v1/categorias-destacadas", "/api/categorias-destacadas"})
@CrossOrigin(origins = "http://localhost:4200")
public class CategoriaDestacadaController {

    @Autowired
    private CategoriaDestacadaService categoriaService;

    @PostMapping
    public ResponseEntity<CategoriaDestacadaDTO> crearCategoria(@RequestBody CategoriaDestacadaDTO dto) {
        CategoriaDestacadaDTO creado = categoriaService.crearCategoria(dto);
        return new ResponseEntity<>(creado, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<CategoriaDestacadaDTO>> obtenerTodas() {
        return new ResponseEntity<>(categoriaService.obtenerTodas(), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoriaDestacadaDTO> obtenerPorId(@PathVariable Long id) {
        return new ResponseEntity<>(categoriaService.obtenerPorId(id), HttpStatus.OK);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoriaDestacadaDTO> actualizarCategoria(
            @PathVariable Long id,
            @RequestBody CategoriaDestacadaDTO dto) {
        CategoriaDestacadaDTO actualizado = categoriaService.actualizarCategoria(id, dto);
        return new ResponseEntity<>(actualizado, HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarCategoria(@PathVariable Long id) {
        categoriaService.eliminarCategoria(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PatchMapping("/{id}/visibilidad")
    public ResponseEntity<CategoriaDestacadaDTO> actualizarVisibilidad(
            @PathVariable Long id,
            @RequestParam Boolean visible) {
        CategoriaDestacadaDTO actualizado = categoriaService.actualizarVisibilidad(id, visible);
        return new ResponseEntity<>(actualizado, HttpStatus.OK);
    }
}
