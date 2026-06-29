package com.upc.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.upc.Entity.*;
import com.upc.Repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class OpenAiService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiService.class);

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.api-url}")
    private String apiUrl;

    @Value("${whatsapp.corporate-number:51933526011}")
    private String corporateNumber;

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private MensajeRepository mensajeRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private PedidoDetalleRepository pedidoDetalleRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String formatNumberCommercial(String number) {
        if (number == null || number.length() < 9) {
            return "933-526-011";
        }
        String clean = number.replaceAll("[^0-9]", "");
        if (clean.startsWith("51") && clean.length() > 9) {
            clean = clean.substring(2);
        }
        if (clean.length() == 9) {
            return clean.substring(0, 3) + "-" + clean.substring(3, 6) + "-" + clean.substring(6);
        }
        return number;
    }

    private String concatenarDireccion(String depto, String prov, String dist, String dir, String ref) {
        StringBuilder sb = new StringBuilder();
        if (depto != null && !depto.trim().isEmpty() && !"null".equalsIgnoreCase(depto)) {
            sb.append("Dpto: ").append(depto.trim());
        }
        if (prov != null && !prov.trim().isEmpty() && !"null".equalsIgnoreCase(prov)) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("Prov: ").append(prov.trim());
        }
        if (dist != null && !dist.trim().isEmpty() && !"null".equalsIgnoreCase(dist)) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("Dist: ").append(dist.trim());
        }
        if (dir != null && !dir.trim().isEmpty() && !"null".equalsIgnoreCase(dir)) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("Dir: ").append(dir.trim());
        }
        if (ref != null && !ref.trim().isEmpty() && !"null".equalsIgnoreCase(ref)) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("Ref: ").append(ref.trim());
        }
        return sb.toString();
    }

    private String getSafeString(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        JsonNode f = node.path(fieldName);
        if (f.isMissingNode() || f.isNull()) {
            return "";
        }
        String txt = f.asText("").trim();
        if ("null".equalsIgnoreCase(txt)) {
            return "";
        }
        return txt;
    }

    @Async
    @Transactional
    public void procesarMensajeConIA(Long chatId, String telefono, String mensajeEntrante) {
        log.info("[OpenAI Service] Iniciando procesamiento asíncrono para el chat ID: {}, Teléfono: {}", chatId, telefono);
        
        if (apiKey == null || apiKey.trim().isEmpty() || (apiKey.startsWith("sk-proj-xpb4m") && apiKey.length() < 30)) {
            log.warn("[OpenAI Service] OpenAI API Key no configurada o incompleta. Se omite llamada.");
            return;
        }

        try {
            // 1. Obtener el chat
            Chat chat = chatRepository.findById(chatId).orElse(null);
            if (chat == null) {
                log.warn("[OpenAI Service] Chat no encontrado para ID: {}", chatId);
                return;
            }

            // 2. Obtener los últimos 10 mensajes del historial del chat (memoria conversacional)
            List<Mensaje> historial = mensajeRepository.findTop10ByChatIdOrderByFechaEnvioDesc(chatId);
            // Revertir para que queden en orden cronológico ascendente (el más antiguo primero)
            Collections.reverse(historial);

            // Reordenamiento de historial con logs cronológicos estrictos
            StringBuilder historialStr = new StringBuilder();
            for (Mensaje msg : historial) {
                historialStr.append(msg.getRemitente())
                            .append(": ")
                            .append(msg.getContenido())
                            .append("\n");
            }
            log.info("Historial enviado a OpenAI: {}", historialStr.toString());

            // 3. Obtener el historial de pedidos y productos del cliente
            List<Pedido> pedidos = pedidoRepository.findByUsuarioTelefono(telefono);
            List<PedidoDetalle> detalles = pedidoDetalleRepository.findByUsuarioTelefono(telefono);

            // Determinar si ya existe un pedido PENDIENTE activo
            Pedido pedidoPendienteActivo = pedidoRepository.findFirstByUsuarioTelefonoAndEstado(telefono, "PENDIENTE");
            boolean tienePedidoPendiente = (pedidoPendienteActivo != null);

            StringBuilder contextoPedidos = new StringBuilder();
            contextoPedidos.append("Historial de pedidos del cliente:\n");
            if (pedidos.isEmpty()) {
                contextoPedidos.append("- No tiene pedidos registrados anteriormente.\n");
            } else {
                for (Pedido ped : pedidos) {
                    contextoPedidos.append("- Pedido ID: ").append(ped.getId())
                                   .append(", Orden: ").append(ped.getNumeroOrden())
                                   .append(", Fecha: ").append(ped.getFecha())
                                   .append(", Estado: ").append(ped.getEstado())
                                   .append(", Total: S/.").append(ped.getTotal())
                                   .append("\n");
                }
                
                contextoPedidos.append("\nProductos específicos comprados por el cliente:\n");
                for (PedidoDetalle det : detalles) {
                    contextoPedidos.append("- Producto: ").append(det.getProducto().getNombre())
                                   .append(" (Pedido ID: ").append(det.getPedido().getId())
                                   .append(", Cantidad: ").append(det.getCantidad())
                                   .append(", Precio Unitario: S/.").append(det.getPrecioUnitario())
                                   .append(")\n");
                }
            }

            // 4. Construir la consulta a OpenAI
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            String formattedCorpNum = formatNumberCommercial(corporateNumber);

            // System prompt del e-commerce corregido con reglas de flujo obligatorias, número de Yape/Plin corporativo y calibración estricta
            String systemPrompt = "Actúa como el asistente inteligente y experto en ventas de nuestro e-commerce 'Punto y Coma'.\n" +
                    "Tu objetivo es guiar al cliente a través de un flujo de compra ordenado por WhatsApp, el cual consta de dos fases principales consecutivas y obligatorias:\n\n" +
                    "1. FASE 'INICIAL': Se activa cuando el cliente saluda, muestra interés en algún producto o envía el mensaje inicial de compra. Tu respuesta en esta fase DEBE saludar cordialmente y solicitar OBLIGATORIAMENTE los datos de entrega faltantes:\n" +
                    "   - Nombre completo del destinatario.\n" +
                    "   - Dirección exacta de entrega.\n" +
                    "   - Teléfono de contacto.\n" +
                    "   - Detalles del producto de interés (Talla, Color, Variante o personalización).\n" +
                    "   *PROHIBIDO solicitar métodos de pago o voucher en este primer mensaje de saludo/inicio. Primero debemos registrar el producto y destino.*\n" +
                    "   *PROHIBIDO improvisar o cambiar el formato del mensaje según el producto. Si estás en la fase INICIAL, la 'respuestaSugerida' DEBE ser exactamente: '¡Hola! Gracias por comprar en Punto y Coma ❤️. Tu pedido se encuentra en proceso de validación. Para cotizar el costo de envío, por favor completa los siguientes datos 🚚:\n\n📍 Departamento:\n📍 Provincia:\n📍 Distrito:\n📍 Dirección exacta:\n📍 Referencia:\n\nCuando termines, envíanos la información por este chat 😊'*\n\n" +
                    "2. FASE 'CONCILIACION': Se activa únicamente si el cliente ya proporcionó los datos de entrega anteriores en el historial o en su mensaje actual, O si menciona proactivamente un método de pago (ej. 'Quiero pagar con Yape', 'Pago por Plin', 'Transferencia'). En esta fase debes:\n" +
                    "   - Extraer todos los datos de entrega y variante detectados.\n" +
                    "   - En tu 'respuestaSugerida', agradece amablemente, confírmale que estás preparando su pedido y guíalo para que realice el pago indicando el método que prefiera (Yape, Plin o Transferencia).\n" +
                    "   *REGLA DE PAGO OBLIGATORIA: Si el cliente elige o menciona que pagará mediante 'Yape' o 'Plin', la 'respuestaSugerida' DEBE incluir de forma obligatoria y explícita que el número celular para realizar el abono por Yape o Plin es " + formattedCorpNum + " (es decir, el mismo número celular por el que se están comunicando, que es el teléfono corporativo real).*\n" +
                    "   - Solicítale amablemente que envíe la captura o voucher del pago por este chat para proceder con el envío.\n\n" +
                    "3. FASE 'RECLAMO_CONSULTA': Se activa si el cliente consulta por el estado de un pedido ya hecho, demoras en el reparto, o si manifiesta algún problema/queja.\n\n" +
                    "REGLA DE FLUJO 1: Si el historial de chat está vacío o no hay un pedido previo 'PENDIENTE' para el cliente (indicado en la información adjunta), debes clasificar obligatoriamente la fase como 'INICIAL' y responder exactamente con la plantilla de saludo y solicitud de datos.\n" +
                    "REGLA DE FLUJO 2: Solo si en el historial o en el mensaje actual el cliente ya envió los datos de envío, puedes avanzar de fase a 'CONCILIACION'.\n\n" +
                    "REGLA ULTRA-ESTRICTA DE CÁLCULO DE PRODUCTO Y PRECIO: Debes ignorar cualquier precio o producto mencionado en los mensajes antiguos del historial al momento de calcular el pedido actual. El cálculo matemático de la lista de productos debe basarse ÚNICAMENTE en el último mensaje entrante del cliente.\n\n" +
                    "Debes retornar UNICAMENTE un objeto JSON de salida:\n" +
                    "{\n" +
                    "  \"fasePedido\": \"INICIAL\" | \"CONCILIACION\" | \"RECLAMO_CONSULTA\",\n" +
                    "  \"datosPedidoSuperficial\": {\n" +
                    "    \"productos\": [\n" +
                    "      {\n" +
                    "        \"productoNombre\": string o null,\n" +
                    "        \"cantidad\": int o null,\n" +
                    "        \"precioUnitario\": double o null,\n" +
                    "        \"subtotal\": double o null\n" +
                    "      }\n" +
                    "    ],\n" +
                    "    \"totalEsperado\": double o null\n" +
                    "  },\n" +
                    "  \"datosConciliacion\": {\n" +
                    "    \"nombreCompleto\": string o null,\n" +
                    "    \"departamento\": string o null,\n" +
                    "    \"provincia\": string o null,\n" +
                    "    \"distrito\": string o null,\n" +
                    "    \"direccionExacta\": string o null,\n" +
                    "    \"referencia\": string o null,\n" +
                    "    \"direccionEntrega\": string o null, // Concatenación de departamento, provincia, distrito, direccionExacta y referencia\n" +
                    "    \"telefonoContacto\": string o null,\n" +
                    "    \"variantesEspecificas\": string o null\n" +
                    "  },\n" +
                    "  \"totalEsperado\": double o null,\n" +
                    "  \"pedidoIdentificado\": boolean,\n" +
                    "  \"direccionDetectada\": boolean,\n" +
                    "  \"datosCompletos\": boolean,\n" +
                    "  \"prioridad\": \"ALTA\" | \"INTERMEDIA\" | \"BAJA\",\n" +
                    "  \"pedidoReferenciadoId\": Long o null,\n" +
                    "  \"respuestaSugerida\": string\n" +
                    "}";

            // Construir el body del request a OpenAI usando Jackson y formateando nativamente
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", "gpt-4o-mini");
            
            ArrayNode messagesArray = objectMapper.createArrayNode();
            
            // 1. System Message Principal
            ObjectNode systemMessage = objectMapper.createObjectNode();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messagesArray.add(systemMessage);
            
            // 2. System Message de Contexto de Compras y Cliente
            ObjectNode contextMessage = objectMapper.createObjectNode();
            contextMessage.put("role", "system");
            contextMessage.put("content", "INFORMACIÓN DEL CLIENTE Y CONTEXTO DE COMPRAS PREVIAS:\n" +
                    "Nombre: " + chat.getNombreCliente() + "\n" +
                    "Teléfono: " + telefono + "\n" +
                    "¿Tiene pedido pendiente activo actualmente?: " + (tienePedidoPendiente ? "SÍ (ID: " + pedidoPendienteActivo.getId() + ")" : "NO") + "\n\n" +
                    contextoPedidos.toString());
            messagesArray.add(contextMessage);
            
            // 3. Mensajes del Historial
            for (Mensaje msg : historial) {
                ObjectNode msgNode = objectMapper.createObjectNode();
                String role = "user";
                if ("ADMINISTRADOR".equalsIgnoreCase(msg.getRemitente())) {
                    role = "assistant";
                }
                msgNode.put("role", role);
                msgNode.put("content", msg.getContenido());
                messagesArray.add(msgNode);
            }
            
            requestBody.set("messages", messagesArray);

            // Forzar respuesta en formato JSON
            ObjectNode responseFormat = objectMapper.createObjectNode();
            responseFormat.put("type", "json_object");
            requestBody.set("response_format", responseFormat);

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);

            log.info("[OpenAI Service] Enviando petición a OpenAI con memoria nativa de {} mensajes...", historial.size());
            String responseStr = restTemplate.postForObject(apiUrl, entity, String.class);

            if (responseStr != null) {
                JsonNode responseJson = objectMapper.readTree(responseStr);
                String contentResult = responseJson.path("choices").get(0).path("message").path("content").asText();
                log.info("[OpenAI Service] Respuesta recibida de OpenAI: {}", contentResult);

                // Parsear el JSON devuelto por OpenAI
                JsonNode aiResult = objectMapper.readTree(contentResult);
                
                String fasePedido = aiResult.path("fasePedido").asText("INICIAL");
                JsonNode datosPedidoSuperficial = aiResult.path("datosPedidoSuperficial");
                JsonNode datosConciliacion = aiResult.path("datosConciliacion");
                
                boolean pedidoIdentificado = aiResult.path("pedidoIdentificado").asBoolean(false);
                boolean direccionDetectada = aiResult.path("direccionDetectada").asBoolean(false);
                boolean datosCompletos = aiResult.path("datosCompletos").asBoolean(false);
                String prioridad = aiResult.path("prioridad").asText("BAJA");
                
                Long pedidoReferenciadoId = null;
                if (aiResult.has("pedidoReferenciadoId") && !aiResult.path("pedidoReferenciadoId").isNull()) {
                    pedidoReferenciadoId = aiResult.path("pedidoReferenciadoId").asLong();
                }
                String respuestaSugerida = aiResult.path("respuestaSugerida").asText("");

                // APLICACIÓN DE REGLAS EXPLICITAS Y PARCHES DE NEGOCIO PARA EVITAR EL BUCLE REPETITIVO
                // Regla de Negocio 1: Si no hay pedido PENDIENTE activo o el historial es ínfimo, forzar fase INICIAL y saludo de solicitud de datos.
                if (!tienePedidoPendiente || historial.size() <= 1) {
                    fasePedido = "INICIAL";
                    respuestaSugerida = "¡Hola! Gracias por comprar en Punto y Coma ❤️. Tu pedido se encuentra en proceso de validación. Para cotizar el costo de envío, por favor completa los siguientes datos 🚚:\n\n📍 Departamento:\n📍 Provincia:\n📍 Distrito:\n📍 Dirección exacta:\n📍 Referencia:\n\nCuando termines, envíanos la información por este chat 😊";
                }

                // 5. Aplicar lógica de base de datos e inserción real según la fase
                if (mensajeEntrante != null && mensajeEntrante.startsWith("[VOUCHER]")) {
                    log.info("[OpenAI Service] Mensaje de tipo VOUCHER detectado. Se delega la lógica al ConciliacionVoucherService. No se altera la estructura de pedidos.");
                } else {
                    if ("INICIAL".equalsIgnoreCase(fasePedido) || pedidoIdentificado) {
                        // Volver a consultar por si fue modificado en base de datos concurrentemente
                        Pedido pedidoPendiente = pedidoRepository.findFirstByUsuarioTelefonoAndEstado(telefono, "PENDIENTE");

                        // Si ya existe un pedido PENDIENTE, pero estamos en fase INICIAL con un nuevo mensaje,
                        // significa que es una nueva solicitud de compra. Lo ponemos en RECHAZADO (no CANCELADO) para que no bloquee.
                        if (pedidoPendiente != null && "INICIAL".equalsIgnoreCase(fasePedido)) {
                            log.info("[OpenAI Service] Se detectó una nueva solicitud de compra en fase INICIAL. Rechazando el pedido PENDIENTE anterior ID: {}", pedidoPendiente.getId());
                            pedidoPendiente.setEstado("RECHAZADO");
                            pedidoRepository.save(pedidoPendiente);
                            pedidoPendiente = null; // Forzar la creación de uno nuevo
                        }

                        if (pedidoPendiente == null) {
                            log.info("[OpenAI Service] Fase INICIAL: Registrando pedido PENDIENTE para el cliente.");
                            
                            Usuario cliente = usuarioRepository.findByTelefono(telefono).orElseGet(() -> {
                                return chat.getUsuario(); // Fallback al usuario mapeado del chat (Admin o Cliente temporal)
                            });

                            double totalEsperado = aiResult.path("totalEsperado").asDouble(0.0);
                            if (totalEsperado <= 0.0) {
                                totalEsperado = datosPedidoSuperficial.path("totalEsperado").asDouble(0.0);
                            }

                            Pedido nuevoPedido = new Pedido();
                            nuevoPedido.setUsuario(cliente);
                            nuevoPedido.setEstado("PENDIENTE");
                            
                            // LocalDateTime forzando zona horaria de Perú (America/Lima)
                            java.time.ZoneId zoneId = java.time.ZoneId.of("America/Lima");
                            nuevoPedido.setFecha(LocalDateTime.now(zoneId));
                            nuevoPedido.setTotal(totalEsperado);
                            nuevoPedido.setNumeroOrden("WSP-" + System.currentTimeMillis());
                            
                            // Parsear y guardar datos de contacto/dirección si están presentes en fase INICIAL
                             String nombreCompleto = getSafeString(datosConciliacion, "nombreCompleto");
                             String departamento = getSafeString(datosConciliacion, "departamento");
                             String provincia = getSafeString(datosConciliacion, "provincia");
                             String distrito = getSafeString(datosConciliacion, "distrito");
                             String direccionExacta = getSafeString(datosConciliacion, "direccionExacta");
                             String referencia = getSafeString(datosConciliacion, "referencia");
                             String telefonoContacto = getSafeString(datosConciliacion, "telefonoContacto");

                             String direccionUnificada = "Dpto: " + departamento + ", Prov: " + provincia + ", Dist: " + distrito + ", Dir: " + direccionExacta + ", Ref: " + referencia;
                             boolean hasAddress = !departamento.isEmpty() || !provincia.isEmpty() || !distrito.isEmpty() || !direccionExacta.isEmpty() || !referencia.isEmpty();
                             if (!hasAddress) {
                                 String directEntrega = getSafeString(datosConciliacion, "direccionEntrega");
                                 if (!directEntrega.isEmpty()) {
                                     direccionUnificada = directEntrega;
                                     hasAddress = true;
                                 }
                             }

                             if (!nombreCompleto.isEmpty()) {
                                 nuevoPedido.setNombreCompleto(nombreCompleto);
                             }
                             if (hasAddress) {
                                 nuevoPedido.setDireccionEntrega(direccionUnificada);
                                 nuevoPedido.setDireccionEnvio(direccionUnificada);
                                 System.out.println("[AUDITORÍA OCR] Dirección unificada guardada en el Pedido (Inicial): " + direccionUnificada);
                             }
                             if (!telefonoContacto.isEmpty()) {
                                 nuevoPedido.setTelefonoContacto(telefonoContacto);
                             }

                            Pedido pedidoGuardado = pedidoRepository.save(nuevoPedido);

                            log.info("[DATABASE] ¡Pedido creado automáticamente en estado PENDIENTE en Supabase!");

                            // Registrar productos en el detalle
                            JsonNode productosNode = datosPedidoSuperficial.path("productos");
                            double sumaTotales = 0.0;
                            StringBuilder sbVariantes = new StringBuilder();

                            if (productosNode.isArray() && productosNode.size() > 0) {
                                for (int i = 0; i < productosNode.size(); i++) {
                                    JsonNode prodNode = productosNode.get(i);
                                    String prodNombre = prodNode.path("productoNombre").asText(null);
                                    int cantidad = prodNode.path("cantidad").asInt(1);
                                    double precioUnitario = prodNode.path("precioUnitario").asDouble(0.0);
                                    if (cantidad <= 0) cantidad = 1;

                                    if (prodNombre != null && !prodNombre.trim().isEmpty() && !"null".equalsIgnoreCase(prodNombre)) {
                                        Producto producto = null;
                                        List<Producto> matching = productoRepository.findAll();
                                        for (Producto p : matching) {
                                            if (p.getNombre() != null && p.getNombre().toLowerCase().contains(prodNombre.toLowerCase())) {
                                                producto = p;
                                                break;
                                            }
                                        }
                                        if (producto == null && !matching.isEmpty()) {
                                            producto = matching.get(0);
                                        }

                                        if (producto != null) {
                                            PedidoDetalle detalle = new PedidoDetalle();
                                            detalle.setPedido(pedidoGuardado);
                                            detalle.setProducto(producto);
                                            detalle.setCantidad(cantidad);
                                            detalle.setPrecioUnitario(precioUnitario > 0 ? precioUnitario : (producto.getPrecio() != null ? producto.getPrecio() : 0.0));
                                            pedidoDetalleRepository.save(detalle);

                                            double subtotal = detalle.getPrecioUnitario() * cantidad;
                                            sumaTotales += subtotal;

                                            if (sbVariantes.length() > 0) sbVariantes.append(", ");
                                            sbVariantes.append(producto.getNombre()).append(" x").append(cantidad);
                                        }
                                    }
                                }
                            } else {
                                // Fallback single product
                                String prodNombre = datosPedidoSuperficial.path("productoNombre").asText(null);
                                int cantidad = datosPedidoSuperficial.path("cantidad").asInt(1);
                                double precioUnitario = datosPedidoSuperficial.path("precioUnitario").asDouble(0.0);
                                if (cantidad <= 0) cantidad = 1;

                                if (prodNombre != null && !prodNombre.trim().isEmpty() && !"null".equalsIgnoreCase(prodNombre)) {
                                    Producto producto = null;
                                    List<Producto> matching = productoRepository.findAll();
                                    for (Producto p : matching) {
                                        if (p.getNombre() != null && p.getNombre().toLowerCase().contains(prodNombre.toLowerCase())) {
                                            producto = p;
                                            break;
                                        }
                                    }
                                    if (producto == null && !matching.isEmpty()) {
                                        producto = matching.get(0);
                                    }

                                    if (producto != null) {
                                        PedidoDetalle detalle = new PedidoDetalle();
                                        detalle.setPedido(pedidoGuardado);
                                        detalle.setProducto(producto);
                                        detalle.setCantidad(cantidad);
                                        detalle.setPrecioUnitario(precioUnitario > 0 ? precioUnitario : (producto.getPrecio() != null ? producto.getPrecio() : 0.0));
                                        pedidoDetalleRepository.save(detalle);

                                        sumaTotales = detalle.getPrecioUnitario() * cantidad;
                                        sbVariantes.append(producto.getNombre()).append(" x").append(cantidad);
                                    }
                                }
                            }

                            if (sumaTotales > 0.0) {
                                pedidoGuardado.setTotal(sumaTotales);
                            }
                            if (sbVariantes.length() > 0) {
                                pedidoGuardado.setVariantesEspecificas(sbVariantes.toString());
                            }
                            pedidoRepository.save(pedidoGuardado);
                            log.info("[OpenAI Service] Total registrado: {}. Variantes: {}", sumaTotales, sbVariantes.toString());
                        }
                    } 
                    else if ("CONCILIACION".equalsIgnoreCase(fasePedido)) {
                        Pedido pedidoExistente = pedidoRepository.findFirstByUsuarioTelefonoAndEstado(telefono, "PENDIENTE");

                        if (pedidoExistente != null) {
                            log.info("[OpenAI Service] Fase CONCILIACION: Actualizando detalles de entrega del pedido ID: {}", pedidoExistente.getId());
                            
                            String nombreCompleto = getSafeString(datosConciliacion, "nombreCompleto");
                            String departamento = getSafeString(datosConciliacion, "departamento");
                            String provincia = getSafeString(datosConciliacion, "provincia");
                            String distrito = getSafeString(datosConciliacion, "distrito");
                            String direccionExacta = getSafeString(datosConciliacion, "direccionExacta");
                            String referencia = getSafeString(datosConciliacion, "referencia");
                            String telefonoContacto = getSafeString(datosConciliacion, "telefonoContacto");
                            String variantesEspecificas = getSafeString(datosConciliacion, "variantesEspecificas");

                            String direccionUnificada = "Dpto: " + departamento + ", Prov: " + provincia + ", Dist: " + distrito + ", Dir: " + direccionExacta + ", Ref: " + referencia;
                            boolean hasAddress = !departamento.isEmpty() || !provincia.isEmpty() || !distrito.isEmpty() || !direccionExacta.isEmpty() || !referencia.isEmpty();
                            if (!hasAddress) {
                                String directEntrega = getSafeString(datosConciliacion, "direccionEntrega");
                                if (!directEntrega.isEmpty()) {
                                    direccionUnificada = directEntrega;
                                    hasAddress = true;
                                }
                            }

                            double totalAI = aiResult.path("totalEsperado").asDouble(0.0);
                            if (totalAI <= 0.0) {
                                totalAI = datosPedidoSuperficial.path("totalEsperado").asDouble(0.0);
                            }

                            if (!nombreCompleto.isEmpty()) {
                                pedidoExistente.setNombreCompleto(nombreCompleto);
                            }
                            if (hasAddress) {
                                pedidoExistente.setDireccionEntrega(direccionUnificada);
                                pedidoExistente.setDireccionEnvio(direccionUnificada); // Asignación explícita mediante alias setter
                                System.out.println("[AUDITORÍA OCR] Dirección unificada guardada en el Pedido (Conciliación): " + direccionUnificada);
                            }
                            if (!telefonoContacto.isEmpty()) {
                                pedidoExistente.setTelefonoContacto(telefonoContacto);
                            }
                            if (!variantesEspecificas.isEmpty()) {
                                pedidoExistente.setVariantesEspecificas(variantesEspecificas);
                            }
                            if (totalAI > 0.0) {
                                pedidoExistente.setTotal(totalAI);
                            }
                            pedidoRepository.save(pedidoExistente);
                            log.info("[DATABASE] ¡ÉXITO! Registro de Pedido PENDIENTE actualizado en Supabase.");
                        }
                    }
                }

                // 6. Actualizar la entidad Chat con los resultados semánticos
                chat.setPedidoIdentificado(pedidoIdentificado);
                chat.setDireccionDetectada(direccionDetectada);
                chat.setDatosCompletos(datosCompletos);
                chat.setPrioridad(prioridad);
                chat.setPedidoReferenciadoId(pedidoReferenciadoId);
                chat.setSugerenciaIa(respuestaSugerida);
                chat.setFasePedido(fasePedido);

                chatRepository.save(chat);
                log.info("[OpenAI Service] Chat ID: {} actualizado en base de datos. Fase: {}, Prioridad: {}", chatId, fasePedido, prioridad);
            }

        } catch (Exception e) {
            log.error("[OpenAI Service] Excepción durante el procesamiento con OpenAI: {}", e.getMessage(), e);
        }
    }

    public String generarSugerenciaMontoFaltante(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pedido no encontrado"));
                
        double total = pedido.getTotal() != null ? pedido.getTotal() : 0.0;
        double detectado = pedido.getMontoDetectado() != null ? pedido.getMontoDetectado() : 0.0;
        double diferencia = pedido.getDiferencia() != null ? pedido.getDiferencia() : (total - detectado);
        
        String nombreCliente = pedido.getNombreCompleto() != null ? pedido.getNombreCompleto() :
                (pedido.getUsuario() != null ? pedido.getUsuario().getNombre() : "Cliente");
                
        String prompt = "Genera un mensaje comercial, empático y muy amable para WhatsApp dirigido a " + nombreCliente + 
                ". El cliente realizó un pago/voucher que fue detectado por nuestro OCR por un monto de S/ " + String.format("%.2f", detectado) + 
                ", pero el total de su pedido es S/ " + String.format("%.2f", total) + 
                ". Queda un saldo pendiente de S/ " + String.format("%.2f", diferencia) + 
                ". Solicítale de manera sumamente atenta y servicial que complete el saldo por Yape o Plin al número " + formatNumberCommercial(corporateNumber) + 
                " para poder proceder con el despacho de su pedido. Devuelve ÚNICAMENTE el texto del mensaje sugerido, sin introducciones ni marcas de formato markdown adicionales.";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", "gpt-4o-mini");
            
            ArrayNode messagesArray = objectMapper.createArrayNode();
            
            ObjectNode systemMessage = objectMapper.createObjectNode();
            systemMessage.put("role", "system");
            systemMessage.put("content", "Eres el asistente inteligente del e-commerce Punto y Coma. Generas respuestas de atención al cliente cortas, empáticas y comerciales.");
            messagesArray.add(systemMessage);
            
            ObjectNode userMessage = objectMapper.createObjectNode();
            userMessage.put("role", "user");
            userMessage.put("content", prompt);
            messagesArray.add(userMessage);
            
            requestBody.set("messages", messagesArray);
            
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);
            String responseStr = restTemplate.postForObject(apiUrl, entity, String.class);
            
            if (responseStr != null) {
                JsonNode responseJson = objectMapper.readTree(responseStr);
                String result = responseJson.path("choices").get(0).path("message").path("content").asText().trim();
                
                // Actualizar la sugerencia en el Chat asociado al usuario de este pedido
                if (pedido.getUsuario() != null && pedido.getUsuario().getTelefono() != null) {
                    chatRepository.findByTelefonoCliente(pedido.getUsuario().getTelefono()).ifPresent(chat -> {
                        chat.setSugerenciaIa(result);
                        chat.setFasePedido("CONCILIACION");
                        chatRepository.save(chat);
                    });
                }
                
                return result;
            }
        } catch (Exception e) {
            log.error("Error generando sugerencia de monto faltante: {}", e.getMessage(), e);
        }
        
        return "Estimado(a) " + nombreCliente + ", detectamos un saldo pendiente de S/ " + String.format("%.2f", diferencia) + ". Por favor complétalo para procesar tu pedido.";
    }
}
