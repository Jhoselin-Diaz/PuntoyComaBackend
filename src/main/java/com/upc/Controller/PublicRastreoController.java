package com.upc.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public/rastreo")
@CrossOrigin(origins = "*")
public class PublicRastreoController {

    @Autowired
    private AdminPedidoController adminPedidoController;

    @GetMapping("/{telefono}")
    public ResponseEntity<List<Map<String, Object>>> consultarRastreoPublico(@PathVariable String telefono) {
        try {
            return adminPedidoController.consultarRastreoPublico(telefono);
        } catch (Exception e) {
            System.err.println("[PublicRastreoController] Error delegando a adminPedidoController: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.ok(Collections.emptyList());
        }
    }
}
