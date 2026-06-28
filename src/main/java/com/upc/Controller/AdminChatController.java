package com.upc.Controller;

import com.upc.DTO.ChatDTO;
import com.upc.DTO.MensajeDTO;
import com.upc.Service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/chats")
@CrossOrigin(origins = "http://localhost:4200")
public class AdminChatController {

    @Autowired
    private ChatService chatService;

    @GetMapping
    public ResponseEntity<List<ChatDTO>> obtenerTodosLosChats() {
        List<ChatDTO> chats = chatService.obtenerTodosLosChats();
        return new ResponseEntity<>(chats, HttpStatus.OK);
    }

    @GetMapping("/{chatId}/mensajes")
    public ResponseEntity<List<MensajeDTO>> obtenerMensajesDeChat(@PathVariable Long chatId) {
        List<MensajeDTO> mensajes = chatService.obtenerMensajesDeChat(chatId);
        return new ResponseEntity<>(mensajes, HttpStatus.OK);
    }

    @PostMapping("/{chatId}/enviar")
    public ResponseEntity<MensajeDTO> enviarMensaje(
            @PathVariable Long chatId,
            @RequestParam(required = false) String mensaje,
            @RequestBody(required = false) Map<String, String> body) {

        String texto = mensaje;
        if (texto == null && body != null) {
            texto = body.get("mensaje");
            if (texto == null) {
                texto = body.get("contenido");
            }
        }

        if (texto == null || texto.trim().isEmpty()) {
            throw new RuntimeException("El texto del mensaje no puede estar vacío");
        }

        MensajeDTO creado = chatService.enviarMensajeAdmin(chatId, texto);
        return new ResponseEntity<>(creado, HttpStatus.CREATED);
    }
}
