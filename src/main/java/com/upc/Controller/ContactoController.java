package com.upc.Controller;

import com.upc.DTO.ContactoBloqueDTO;
import com.upc.DTO.ContactoCierreDTO;
import com.upc.Service.ContactoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/content/contacto")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class ContactoController {

    @Autowired
    private ContactoService contactoService;

    @GetMapping("/bloques")
    public ResponseEntity<List<ContactoBloqueDTO>> obtenerBloques() {
        return new ResponseEntity<>(contactoService.obtenerBloques(), HttpStatus.OK);
    }

    @PutMapping("/bloques/{id}")
    public ResponseEntity<ContactoBloqueDTO> actualizarBloque(@PathVariable String id, @RequestBody ContactoBloqueDTO dto) {
        return new ResponseEntity<>(contactoService.actualizarBloque(id, dto), HttpStatus.OK);
    }

    @GetMapping("/cierre")
    public ResponseEntity<ContactoCierreDTO> obtenerCierre() {
        return new ResponseEntity<>(contactoService.obtenerCierre(), HttpStatus.OK);
    }

    @PutMapping("/cierre")
    public ResponseEntity<ContactoCierreDTO> actualizarCierre(@RequestBody ContactoCierreDTO dto) {
        return new ResponseEntity<>(contactoService.actualizarCierre(dto), HttpStatus.OK);
    }
}
