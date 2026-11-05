package com.upc.Controller;

import com.upc.DTO.WhatsappStatusDTO;
import com.upc.Service.WhatsappService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para la gestión y monitoreo del estado de WhatsApp Web.
 * Expone endpoints para el panel de administración de Angular.
 */
@RestController
@RequestMapping("/api/admin/whatsapp")
@CrossOrigin(origins = {"http://localhost:4200", "https://puntoycoma-store.netlify.app", "https://puntoycoma-store.netlify.app/"})
public class AdminWhatsappController {

    @Autowired
    private WhatsappService whatsappService;

    /**
     * Consulta el estado actual de la conexión de WhatsApp Web y obtiene el código QR en Base64
     * cuando el estado sea "QR_READY".
     *
     * @return ResponseEntity con WhatsappStatusDTO ({ status, qrCodeBase64 }).
     */
    @GetMapping("/status")
    public ResponseEntity<WhatsappStatusDTO> obtenerEstadoWhatsapp() {
        WhatsappStatusDTO statusDTO = whatsappService.obtenerEstadoWhatsapp();
        return ResponseEntity.ok(statusDTO);
    }

    /**
     * Cierra la sesión activa de WhatsApp Web en el puente Node.js.
     */
    @org.springframework.web.bind.annotation.PostMapping("/desconectar")
    public ResponseEntity<java.util.Map<String, Object>> desconectarWhatsapp() {
        boolean exito = whatsappService.desconectarWhatsapp();
        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("success", exito);
        resp.put("message", exito ? "Sesión de WhatsApp desconectada exitosamente" : "No se pudo desconectar WhatsApp");
        return ResponseEntity.ok(resp);
    }
}
