package com.upc.Service;

import com.upc.DTO.Whatsapp.*;
import com.upc.Entity.Chat;
import com.upc.Entity.Mensaje;
import com.upc.Entity.Usuario;
import com.upc.Repository.ChatRepository;
import com.upc.Repository.MensajeRepository;
import com.upc.Repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Implementación del servicio de WhatsApp Cloud API.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Procesar mensajes entrantes del webhook (método procesarPayload).</li>
 *   <li>Enviar mensajes de texto hacia clientes (método enviarMensajeTexto).</li>
 * </ul>
 */
@Service
public class WhatsappServiceImpl implements WhatsappService {

    private static final Logger log = LoggerFactory.getLogger(WhatsappServiceImpl.class);

    // ── Inyección de credenciales desde application.properties ───────────────
    @Value("${whatsapp.phone-number-id}")
    private String phoneNumberId;

    @Value("${whatsapp.access-token}")
    private String accessToken;

    @Value("${whatsapp.api-url}")
    private String apiUrl;

    private final RestTemplate restTemplate;
    private final ChatRepository chatRepository;
    private final MensajeRepository mensajeRepository;
    private final UsuarioRepository usuarioRepository;

    public WhatsappServiceImpl(RestTemplate restTemplate, ChatRepository chatRepository, MensajeRepository mensajeRepository, UsuarioRepository usuarioRepository) {
        this.restTemplate = restTemplate;
        this.chatRepository = chatRepository;
        this.mensajeRepository = mensajeRepository;
        this.usuarioRepository = usuarioRepository;
    }

    // =========================================================================
    // RECEPCIÓN — Procesamiento del webhook entrante
    // =========================================================================

    @Override
    @Transactional
    public void procesarPayload(WhatsappPayloadDTO payload) {

        if (payload == null || payload.getEntry() == null) {
            log.warn("[WhatsApp] Payload vacío o nulo recibido. Se ignora.");
            return;
        }

        // Iterar sobre cada "entry" (normalmente solo hay una)
        for (WhatsappEntryDTO entry : payload.getEntry()) {

            if (entry.getChanges() == null) continue;

            for (WhatsappChangeDTO change : entry.getChanges()) {

                // Solo procesar eventos del campo "messages"
                if (!"messages".equals(change.getField())) {
                    log.debug("[WhatsApp] Change de campo '{}' ignorado.", change.getField());
                    continue;
                }

                WhatsappValueDTO value = change.getValue();
                if (value == null || value.getMessages() == null) continue;

                List<WhatsappMessageDTO> messages = value.getMessages();

                for (WhatsappMessageDTO mensaje : messages) {
                    procesarMensajeIndividual(mensaje, value);
                }
            }
        }
    }

    // =========================================================================
    // ENVÍO — Llamada a la API de WhatsApp Cloud de Meta
    // =========================================================================

    /**
     * Envía un mensaje de plantilla (template) a un número de WhatsApp usando la
     * Graph API de Meta.
     *
     * <p>NOTA SANDBOX: Meta bloquea mensajes de tipo "text" libres hasta que el
     * número esté verificado en producción. En Sandbox el primer mensaje saliente
     * debe ser siempre un template aprobado.
     *
     * <p>Template usado: {@code jaspers_market_order_confirmation_v1} (en_US)
     *
     * <p>Formato del cuerpo JSON enviado a Meta:
     * <pre>
     * {
     *   "messaging_product": "whatsapp",
     *   "to": "51987654321",
     *   "type": "template",
     *   "template": {
     *     "name": "jaspers_market_order_confirmation_v1",
     *     "language": { "code": "en_US" },
     *     "components": [{
     *       "type": "body",
     *       "parameters": [
     *         { "type": "text", "text": "Cliente Tesis" },
     *         { "type": "text", "text": "123456" },
     *         { "type": "text", "text": "28/06/2026" }
     *       ]
     *     }]
     *   }
     * }
     * </pre>
     *
     * @param destinatario Número en formato E.164 sin '+' (ej. "51987654321").
     * @param texto        Texto libre a enviar. Máximo 4096 caracteres.
     *                     Válido dentro de la ventana de 24 h tras el último mensaje del cliente.
     * @return Respuesta de Meta con el wamid del mensaje enviado.
     * @throws RuntimeException Si Meta devuelve un error HTTP (4xx/5xx).
     */
    @Override
    public WhatsappApiResponseDTO enviarMensajeTexto(String destinatario, String texto) {

        // ── 1. Construir la URL ───────────────────────────────────────────────
        String url = apiUrl + "/" + phoneNumberId + "/messages";

        // ── 2. Body de texto libre (válido dentro de la ventana de 24 h) ────────
        //    Meta permite type=text cuando el cliente nos escribió primero.
        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type",    "individual",
                "to",                destinatario,
                "type",              "text",
                "text",              Map.of(
                        "preview_url", false,
                        "body",        texto
                )
        );

        // ── 3. Construir las cabeceras HTTP ───────────────────────────────────
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);           // → Authorization: Bearer {token}

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        // ── 4. Ejecutar la petición POST ──────────────────────────────────────
        try {
            log.info("[WhatsApp] Enviando texto libre a {} | mensaje: \"{}\"", destinatario, texto);

            ResponseEntity<WhatsappApiResponseDTO> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    WhatsappApiResponseDTO.class
            );

            WhatsappApiResponseDTO respuesta = response.getBody();

            if (respuesta != null && respuesta.getMessages() != null && !respuesta.getMessages().isEmpty()) {
                String wamid = respuesta.getMessages().get(0).getId();
                log.info("[WhatsApp] ✅ Mensaje enviado correctamente. wamid: {}", wamid);
            }

            return respuesta;

        } catch (HttpClientErrorException ex) {
            // Error del lado del cliente (401 token inválido, 400 número mal formado, etc.)
            log.error("[WhatsApp] ❌ Error HTTP {} al enviar mensaje a {}: {}",
                    ex.getStatusCode(), destinatario, ex.getResponseBodyAsString());
            throw new RuntimeException("Error al enviar mensaje por WhatsApp: " + ex.getResponseBodyAsString(), ex);
        } catch (Exception ex) {
            log.error("[WhatsApp] ❌ Error inesperado al enviar mensaje a {}: {}", destinatario, ex.getMessage());
            throw new RuntimeException("Error inesperado al enviar mensaje por WhatsApp.", ex);
        }
    }

    // =========================================================================
    // Lógica privada de procesamiento de mensajes entrantes
    // =========================================================================

    /**
     * Procesa un mensaje individual extrayendo el número del remitente
     * y el texto. Aquí es donde añadirás tu lógica de negocio.
     */
    private void procesarMensajeIndividual(WhatsappMessageDTO mensaje, WhatsappValueDTO value) {

        String telefono     = mensaje.getFrom();
        String nombreCliente = extraerNombreContacto(value, telefono);
        String tipoMensaje  = mensaje.getType();

        if ("text".equals(tipoMensaje) && mensaje.getText() != null) {

            String textoRecibido = mensaje.getText().getBody();

            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("[WhatsApp] Mensaje recibido");
            log.info("  📞 Teléfono  : {}", telefono);
            log.info("  👤 Nombre    : {}", nombreCliente != null ? nombreCliente : "(desconocido)");
            log.info("  💬 Mensaje   : {}", textoRecibido);
            log.info("  🆔 wamid     : {}", mensaje.getId());
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            // Guardar en base de datos: Buscar o crear el chat
            Chat chat = chatRepository.findByTelefonoCliente(telefono)
                    .orElseGet(() -> {
                        Chat nuevoChat = new Chat();
                        nuevoChat.setTelefonoCliente(telefono);
                        nuevoChat.setNombreCliente((nombreCliente != null && !nombreCliente.trim().isEmpty()) ? nombreCliente : "Cliente Nuevo");
                        nuevoChat.setUnreadCount(0);
                        
                        // Buscar o crear usuario administrador por defecto
                        Usuario admin = usuarioRepository.findById(1L)
                                .orElseGet(() -> {
                                    List<Usuario> todos = usuarioRepository.findAll();
                                    if (!todos.isEmpty()) {
                                        return todos.get(0);
                                    }
                                    Usuario mockAdmin = new Usuario();
                                    mockAdmin.setNombre("Admin Principal");
                                    mockAdmin.setEmail("admin@puntoycoma.com");
                                    mockAdmin.setPassword("password");
                                    mockAdmin.setRol("ADMIN");
                                    mockAdmin.setActivo(true);
                                    mockAdmin.setHabilitado(true);
                                    return usuarioRepository.save(mockAdmin);
                                });
                        nuevoChat.setUsuario(admin);
                        return nuevoChat;
                    });

            // Actualizar metadatos del chat
            chat.setUltimoMensaje(textoRecibido);
            chat.setFechaUltimaActualizacion(LocalDateTime.now());
            chat.setUnreadCount(chat.getUnreadCount() + 1);
            Chat chatGuardado = chatRepository.save(chat);

            // Guardar burbuja de mensaje
            Mensaje msg = new Mensaje();
            msg.setChat(chatGuardado);
            msg.setContenido(textoRecibido);
            msg.setRemitente("CLIENTE");
            msg.setFechaEnvio(LocalDateTime.now());
            msg.setWamid(mensaje.getId());
            mensajeRepository.save(msg);

        } else {
            log.info("[WhatsApp] Mensaje de tipo '{}' recibido de {}. No se procesa texto.", tipoMensaje, telefono);
        }
    }

    /**
     * Busca el nombre del contacto en el array "contacts" del value,
     * haciendo match por wa_id con el número del mensaje.
     */
    private String extraerNombreContacto(WhatsappValueDTO value, String telefono) {
        if (value.getContacts() == null) return null;

        return value.getContacts().stream()
                .filter(c -> telefono.equals(c.getWaId()))
                .findFirst()
                .map(c -> c.getProfile() != null ? c.getProfile().getName() : null)
                .orElse(null);
    }
}
