package com.upc.Controller;

import com.upc.Entity.ConfiguracionCarrito;
import com.upc.Service.ConfiguracionCarritoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/configuracion-carrito", "/api/configuracion-carrito"})
@CrossOrigin(origins = "http://localhost:4200")
public class ConfiguracionCarritoController {

    @Autowired
    private ConfiguracionCarritoService service;

    @GetMapping
    public ResponseEntity<ConfiguracionCarrito> obtenerConfiguracion() {
        return new ResponseEntity<>(service.obtenerConfiguracion(), HttpStatus.OK);
    }

    @PutMapping
    public ResponseEntity<ConfiguracionCarrito> guardarConfiguracion(@RequestBody ConfiguracionCarrito config) {
        ConfiguracionCarrito guardada = service.guardarConfiguracion(config);
        return new ResponseEntity<>(guardada, HttpStatus.OK);
    }
}
