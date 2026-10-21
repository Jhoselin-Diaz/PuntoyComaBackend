package com.upc.Controller;

import com.upc.DTO.DashboardVigilanciaDTO;
import com.upc.DTO.ProductoVigilanciaDTO;
import com.upc.Service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/dashboard")
@CrossOrigin(origins = {"http://localhost:4200", "https://puntoycoma-store.netlify.app", "https://puntoycoma-store.netlify.app/"})
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/vigilancia-stock")
    public ResponseEntity<DashboardVigilanciaDTO> getVigilanciaStock() {
        return ResponseEntity.ok(dashboardService.obtenerVigilanciaStock());
    }

    @GetMapping("/mas-vendidos")
    public ResponseEntity<List<ProductoVigilanciaDTO>> getMasVendidos() {
        return ResponseEntity.ok(dashboardService.obtenerMasVendidos());
    }

    @GetMapping("/agotandose")
    public ResponseEntity<List<ProductoVigilanciaDTO>> getAgotandose() {
        return ResponseEntity.ok(dashboardService.obtenerAgotandose());
    }

    @GetMapping("/sin-existencias")
    public ResponseEntity<List<ProductoVigilanciaDTO>> getSinExistencias() {
        return ResponseEntity.ok(dashboardService.obtenerSinExistencias());
    }
}
