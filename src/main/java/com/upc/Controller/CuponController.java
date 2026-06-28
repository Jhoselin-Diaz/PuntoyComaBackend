package com.upc.Controller;

import com.upc.Entity.Cupon;
import com.upc.Service.CuponService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping({"/api/v1/cupones", "/api/cupones"})
@CrossOrigin(origins = "http://localhost:4200")
public class CuponController {

    @Autowired
    private CuponService service;

    @GetMapping
    public ResponseEntity<List<Cupon>> listarTodos() {
        return new ResponseEntity<>(service.listarTodos(), HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<Cupon> guardar(@RequestBody Cupon cupon) {
        return new ResponseEntity<>(service.guardar(cupon), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Cupon> actualizar(@PathVariable Long id, @RequestBody Cupon cupon) {
        return new ResponseEntity<>(service.actualizar(id, cupon), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @GetMapping("/validar/{codigo}")
    public ResponseEntity<Cupon> validarCupon(@PathVariable String codigo) {
        return service.validarCupon(codigo)
                .map(cupon -> new ResponseEntity<>(cupon, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
}
