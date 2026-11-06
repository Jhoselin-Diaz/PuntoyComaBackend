package com.upc.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/public/rastreo")
@CrossOrigin(origins = "*")
public class PublicRastreoController {

    @Autowired
    private AdminPedidoController adminPedidoController;

    @GetMapping("/{telefono}")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> consultarRastreoPublico(@PathVariable String telefono) {
        try {
            if (telefono == null || telefono.trim().isEmpty()) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            ResponseEntity<List<Map<String, Object>>> todosResp = adminPedidoController.obtenerPedidosDeCliente(telefono);
            List<Map<String, Object>> todos = todosResp != null ? todosResp.getBody() : null;
            if (todos == null || todos.isEmpty()) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            LocalDateTime ahora = LocalDateTime.now();

            List<Map<String, Object>> activos = todos.stream().filter(p -> {
                String est = (String) p.get("estado");
                String estRaw = (String) p.get("estadoRaw");
                boolean esEnCamino = "En camino".equalsIgnoreCase(est) || "EN_CAMINO".equalsIgnoreCase(est) || "EN_CAMINO".equalsIgnoreCase(estRaw);
                String guia = (String) p.get("numeroGuia");
                if (guia == null || guia.trim().isEmpty()) {
                    guia = (String) p.get("guia");
                }
                String agencia = (String) p.get("agenciaEnvio");
                if (agencia == null || agencia.trim().isEmpty()) {
                    agencia = (String) p.get("agencia");
                }
                boolean tieneGuiaYAgencia = (agencia != null && !agencia.trim().isEmpty()) && (guia != null && !guia.trim().isEmpty());

                if (!esEnCamino && !tieneGuiaYAgencia) return false;
                if ("Entregado".equalsIgnoreCase(est) || "ENTREGADO".equalsIgnoreCase(est) || "ENTREGADO".equalsIgnoreCase(estRaw)) return false;
                if ("Rechazado".equalsIgnoreCase(est) || "RECHAZADO".equalsIgnoreCase(est) || "Cancelado".equalsIgnoreCase(est)) return false;

                // Verificar expiración si existe
                Object expObj = p.get("fechaExpiracionRastreo");
                if (expObj != null) {
                    try {
                        String expStr = expObj.toString().trim();
                        if (!expStr.isEmpty()) {
                            LocalDateTime expDate = LocalDateTime.parse(expStr);
                            if (expDate.isBefore(ahora)) {
                                return false;
                            }
                        }
                    } catch (Exception ignored) {}
                }

                return true;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(activos);
        } catch (Exception e) {
            System.err.println("[PublicRastreoController] Error en consultarRastreoPublico: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.ok(Collections.emptyList());
        }
    }
}
