package com.upc.Controller;

import com.upc.Entity.ConfiguracionNegocio;
import com.upc.Service.ConfiguracionNegocioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/configuracion", "/api/configuracion"})
@CrossOrigin(origins = "http://localhost:4200")
public class ConfiguracionNegocioController {

    @Autowired
    private ConfiguracionNegocioService configuracionService;

    @GetMapping
    public ResponseEntity<ConfiguracionNegocio> obtenerConfiguracion() {
        return new ResponseEntity<>(configuracionService.obtenerConfiguracion(), HttpStatus.OK);
    }

    @PutMapping
    public ResponseEntity<ConfiguracionNegocio> guardarConfiguracion(@RequestBody ConfiguracionNegocio configuracion) {
        ConfiguracionNegocio guardada = configuracionService.guardarConfiguracion(configuracion);
        return new ResponseEntity<>(guardada, HttpStatus.OK);
    }
}
