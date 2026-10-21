package com.upc.Controller;

import com.upc.DTO.ProductoVigilanciaDTO;
import com.upc.Service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/inventario", "/api/admin/inventario"})
@CrossOrigin(origins = {"http://localhost:4200", "https://puntoycoma-store.netlify.app", "https://puntoycoma-store.netlify.app/"})
@RequiredArgsConstructor
public class InventarioController {

    private final DashboardService dashboardService;

    @GetMapping("/resumen")
    public ResponseEntity<Map<String, Object>> obtenerResumenInventario() {
        List<ProductoVigilanciaDTO> masVendidos = dashboardService.obtenerMasVendidos();
        ProductoVigilanciaDTO productoEstrella = (masVendidos != null && !masVendidos.isEmpty())
                ? masVendidos.get(0)
                : null;

        Map<String, Object> resumen = new HashMap<>();
        resumen.put("productoEstrella", productoEstrella);
        resumen.put("masVendidos", masVendidos);
        resumen.put("agotandose", dashboardService.obtenerAgotandose());
        resumen.put("sinExistencias", dashboardService.obtenerSinExistencias());

        return ResponseEntity.ok(resumen);
    }
}
