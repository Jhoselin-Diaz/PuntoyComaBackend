package com.upc.Controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.upc.Service.ChatService;
import com.upc.Service.WhatsappService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST para el Webhook de WhatsApp Cloud API.
 * <p>
 * Expone endpoints bajo la ruta base /webhook y /webhook/:
 * <ul>
 *   <li>GET  /webhook — Verificación del webhook por parte de Meta.</li>
 *   <li>POST /webhook — Recepción y procesamiento de eventos/mensajes entrantes.</li>
 *   <li>GET  /webhook/enviar — Envío manual para pruebas del admin.</li>
 * </ul>
 *
 * Referencia oficial: https://developers.facebook.com/docs/whatsapp/cloud-api/webhooks
 */
@RestController
@RequestMapping({"/webhook", "/webhook/"})
@CrossOrigin(origins = "http://localhost:4200")
public class WebhookWhatsappController {

    private static final Logger log = LoggerFactory.getLogger(WebhookWhatsappController.class);

    /**
     * Token secreto que debe coincidir con el valor configurado
     * en el panel de Meta for Developers → Webhook → Verify Token.
     */
    private static final String VERIFY_TOKEN = "mi_token_secreto_tesis";

    private final WhatsappService whatsappService;
    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    public WebhookWhatsappController(WhatsappService whatsappService, ChatService chatService) {
        this.whatsappService = whatsappService;
        this.chatService = chatService;
        this.objectMapper = new ObjectMapper();
    }

    // -------------------------------------------------------------------------
    // GET /webhook  →  Verificación del Webhook por Meta
    // -------------------------------------------------------------------------

    /**
     * Meta envía una petición GET con estos query-params para confirmar
     * que el servidor es el propietario legítimo del webhook.
     *
     * @param mode      Siempre "subscribe" cuando Meta verifica.
     * @param token     El token que configuraste en el panel de Meta.
     * @param challenge Cadena aleatoria que debes devolver tal cual.
     * @return El challenge como texto plano (HTTP 200) si el token es válido,
     *         o HTTP 403 si no coincide.
     */
    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verificarWebhook(
            @RequestParam(name = "hub.mode")         String mode,
            @RequestParam(name = "hub.verify_token") String token,
            @RequestParam(name = "hub.challenge")    String challenge) {

        log.info("[WhatsApp Webhook] GET de verificación recibido — mode={}, token={}", mode, token);

        HttpHeaders headers = new HttpHeaders();
        headers.add("ngrok-skip-browser-warning", "true");

        if ("subscribe".equals(mode) && VERIFY_TOKEN.equals(token)) {
            log.info("[WhatsApp Webhook] Verificación exitosa. Devolviendo challenge: {}", challenge);
            return ResponseEntity.ok().headers(headers).body(challenge);
        }

        log.warn("[WhatsApp Webhook] Verificación fallida. Token inválido o mode incorrecto.");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).headers(headers).body("Token de verificación inválido.");
    }

    // -------------------------------------------------------------------------
    // POST /webhook  →  Recepción de mensajes / eventos entrantes
    // -------------------------------------------------------------------------

    /**
     * Meta envía una petición POST cada vez que ocurre un evento suscripto.
     * <p>
     * Recibe la petición como String crudo para asegurar su registro en los logs
     * antes de cualquier parsing y evitar respuestas 400 silenciosas por fallos de conversión.
     *
     * @param rawPayload Cuerpo crudo del JSON enviado por Meta.
     * @return HTTP 200 OK — Meta espera esta respuesta en menos de 5 segundos.
     */
    @PostMapping
    public ResponseEntity<String> recibirMensaje(@RequestBody(required = false) String rawPayload) {
        System.out.println(">>> [Java Webhook] POST /webhook INTERCEPTADO EXPLICITAMENTE! rawPayload: " + rawPayload);
        log.info("[WhatsApp Webhook] POST recibido - Raw Payload: {}", rawPayload);

        HttpHeaders headers = new HttpHeaders();
        headers.add("ngrok-skip-browser-warning", "true");

        if (rawPayload == null || rawPayload.trim().isEmpty()) {
            log.warn("[WhatsApp Webhook] Cuerpo de petición vacío recibido.");
            return ResponseEntity.ok().headers(headers).body("EVENT_RECEIVED");
        }

        try {
            // Deserializar manualmente
            JsonNode payload = objectMapper.readTree(rawPayload);

            // ─── 1. DETECCIÓN AUTOMÁTICA DE FORMATO: EVOLUTION API ───────────
            if (payload.has("event")) {
                String event = payload.path("event").asText();
                log.info("[WhatsApp Webhook] Formato Evolution API detectado - Evento: {}", event);

                if ("messages.upsert".equals(event)) {
                    JsonNode dataNode = payload.path("data");
                    if (dataNode != null) {
                        JsonNode keyNode = dataNode.path("key");
                        String remoteJid = keyNode.path("remoteJid").asText();
                        String wamid = keyNode.path("id").asText();
                        boolean fromMe = keyNode.path("fromMe").asBoolean(false);

                        // Limpiar número de teléfono de forma agresiva (ej. "51933526011@c.us" -> "51933526011")
                        String telefono = (remoteJid != null) ? remoteJid.split("@")[0].replaceAll("[^0-9]", "") : null;

                        // Obtener el LID original si fue enviado
                        String lid = null;
                        if (dataNode.has("lid")) {
                            String rawLid = dataNode.path("lid").asText();
                            if (rawLid != null && !rawLid.trim().isEmpty()) {
                                lid = rawLid.split("@")[0].replaceAll("[^0-9]", "");
                            }
                        }

                        // Nombre de perfil pushName
                        String nombre = dataNode.path("pushName").asText("Cliente WhatsApp");
                        if (nombre == null || nombre.trim().isEmpty()) {
                            nombre = "Cliente WhatsApp";
                        }

                        // Determinar remitente basándonos en fromMe
                        String remitente = fromMe ? "ADMINISTRADOR" : "CLIENTE";

                        // Extraer contenido según el tipo de mensaje
                        JsonNode messageNode = dataNode.path("message");
                        String texto = null;

                        if (messageNode != null) {
                            if (messageNode.has("conversation")) {
                                texto = messageNode.path("conversation").asText();
                            } else if (messageNode.has("extendedTextMessage")) {
                                texto = messageNode.path("extendedTextMessage").path("text").asText();
                            } else if (messageNode.has("imageMessage")) {
                                JsonNode imgMsg = messageNode.path("imageMessage");
                                String mediaUrl = imgMsg.path("mediaUrl").asText();
                                if (mediaUrl == null || mediaUrl.trim().isEmpty()) {
                                    mediaUrl = imgMsg.path("url").asText();
                                }
                                String caption = imgMsg.path("caption").asText();

                                // Opción B: Prefijar con [VOUCHER] URL de imagen
                                texto = "[VOUCHER] " + mediaUrl;
                                if (caption != null && !caption.trim().isEmpty()) {
                                    texto += " | " + caption;
                                }
                            } else if (messageNode.has("documentMessage")) {
                                JsonNode docMsg = messageNode.path("documentMessage");
                                String mediaUrl = docMsg.path("mediaUrl").asText();
                                String fileName = docMsg.path("fileName").asText();
                                texto = "[DOCUMENTO] " + mediaUrl;
                                if (fileName != null && !fileName.trim().isEmpty()) {
                                    texto += " | " + fileName;
                                }
                            }
                        }

                        if (telefono != null && texto != null && !texto.trim().isEmpty()) {
                            log.info("[WhatsApp Webhook] Evolution API - Guardando mensaje - Remitente: {} | Teléfono: {} | LID: {} | Contenido: {}", remitente, telefono, lid, texto);
                            chatService.registrarMensaje(telefono, lid, nombre, texto, wamid, remitente);
                        }
                    }
                }
            }
            // ─── 2. DETECCIÓN AUTOMÁTICA DE FORMATO: META CLOUD API ──────────
            else {
                log.info("[WhatsApp Webhook] Formato Meta Cloud API detectado.");

                JsonNode entryNode = payload.path("entry").get(0);
                if (entryNode != null) {
                    JsonNode changeNode = entryNode.path("changes").get(0);
                    if (changeNode != null) {
                        JsonNode valueNode = changeNode.path("value");

                        // Si contiene la lista 'messages', extrae el primer mensaje: messages[0]
                        if (valueNode != null && valueNode.has("messages")) {
                            JsonNode messageNode = valueNode.path("messages").get(0);
                            if (messageNode != null) {

                                // Obtener el teléfono del remitente de message.from o contacts[0].wa_id
                                String telefono = null;
                                if (messageNode.has("from")) {
                                    telefono = messageNode.path("from").asText();
                                }

                                JsonNode contactsNode = valueNode.path("contacts");
                                JsonNode contactNode = contactsNode != null ? contactsNode.get(0) : null;

                                if (telefono == null && contactNode != null && contactNode.has("wa_id")) {
                                    telefono = contactNode.path("wa_id").asText();
                                }

                                // Limpiar número de teléfono de forma agresiva
                                if (telefono != null) {
                                    telefono = telefono.split("@")[0].replaceAll("[^0-9]", "");
                                }

                                // Obtener nombre de contact -> profile -> name
                                String nombre = "Cliente WhatsApp";
                                if (contactNode != null) {
                                    JsonNode profileNode = contactNode.path("profile");
                                    if (profileNode != null && profileNode.has("name")) {
                                        nombre = profileNode.path("name").asText();
                                    }
                                }

                                if ("Jhoselin".equalsIgnoreCase(nombre)) {
                                    nombre = "Jhoselin";
                                }

                                // Obtener texto de message -> text -> body o button
                                String texto = null;
                                if (messageNode.has("text")) {
                                    texto = messageNode.path("text").path("body").asText();
                                } else if (messageNode.has("button")) {
                                    texto = messageNode.path("button").path("text").asText();
                                }

                                String wamid = messageNode.path("id").asText();

                                if (telefono != null && texto != null && !texto.trim().isEmpty()) {
                                    log.info("[WhatsApp Webhook] Meta API - Guardando mensaje - Remitente: CLIENTE | Teléfono: {} | Contenido: {}", telefono, texto);
                                    chatService.registrarMensaje(telefono, nombre, texto, wamid, "CLIENTE");
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("[WhatsApp Webhook] Error crítico parseando o persistiendo payload: {}", e.getMessage(), e);
        }

        // Siempre responder 200 OK con la cabecera de Ngrok
        return ResponseEntity.ok().headers(headers).body("EVENT_RECEIVED");
    }

    // -------------------------------------------------------------------------
    // GET /webhook/enviar  →  Envío de mensajes de salida para pruebas
    // -------------------------------------------------------------------------

    /**
     * Endpoint temporal GET para enviar un mensaje de WhatsApp fácilmente desde el navegador.
     *
     * @param destinatario Número de teléfono en formato E.164 sin '+'.
     * @param mensaje      Texto del mensaje a enviar.
     * @return HTTP 200 con el wamid si el envío fue exitoso, o HTTP 500 si falló.
     */
    @GetMapping("/enviar")
    public ResponseEntity<?> enviarMensaje(
            @RequestParam String destinatario,
            @RequestParam String mensaje) {

        log.info("[WhatsApp] Solicitud de envío — destinatario: {}", destinatario);

        try {
            var respuesta = whatsappService.enviarMensajeTexto(destinatario, mensaje);
            return ResponseEntity.ok(respuesta);
        } catch (RuntimeException ex) {
            log.error("[WhatsApp] Error al enviar mensaje: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ex.getMessage());
        }
    }
}
