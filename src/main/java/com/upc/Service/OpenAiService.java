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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private PagoVoucherRepository pagoVoucherRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    public static final String PLANTILLA_DATOS_ENTREGA =
            "📍 Nombre completo:\n" +
            "📍 DNI:\n" +
            "📍 Teléfono:\n" +
            "📍 Departamento:\n" +
            "📍 Provincia:\n" +
            "📍 Distrito:\n" +
            "📍 Dirección exacta:\n" +
            "📍 Referencias:";

    public static Integer parsearNumeroOPalabra(String token) {
        if (token == null) return null;
        String t = java.text.Normalizer.normalize(token.toLowerCase().trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        if (t.matches("^\\d+$")) {
            try { return Integer.parseInt(t); } catch (Exception ignored) {}
        }
        switch (t) {
            case "un":
            case "uno":
            case "una":
                return 1;
            case "dos":
                return 2;
            case "tres":
                return 3;
            case "cuatro":
                return 4;
            case "cinco":
                return 5;
            case "seis":
                return 6;
            case "siete":
                return 7;
            case "ocho":
                return 8;
            case "nueve":
                return 9;
            case "diez":
                return 10;
            default:
                return null;
        }
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, Object> locks = new java.util.concurrent.ConcurrentHashMap<>();

    @jakarta.annotation.PostConstruct
    public void init() {
        this.transactionTemplate = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    }

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

    private String detectarDecisionPedidoExplicita(String mensajeEntrante) {
        if (mensajeEntrante == null || mensajeEntrante.trim().isEmpty()) {
            return "DESCONOCIDO";
        }
        String m = mensajeEntrante.toLowerCase().trim();
        m = java.text.Normalizer.normalize(m, java.text.Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        // 1. Patrones explícitos para NUEVO
        if (m.equals("nuevo") || 
            m.contains("pedido nuevo") || 
            m.contains("nuevo pedido") || 
            m.contains("otro pedido") || 
            m.contains("hazme otro pedido") || 
            m.contains("comprar aparte") || 
            m.contains("pedido independiente") || 
            m.contains("opcion 2") || 
            m.equals("2")) {
            return "NUEVO";
        }

        // 2. Patrones explícitos para CONTINUAR
        if (m.equals("continuar") || 
            m.contains("quiero continuar") || 
            m.contains("sigue con el anterior") || 
            m.contains("continuar con el pedido") || 
            m.contains("agrega esto a mi pedido") || 
            m.contains("agregalo al pedido") || 
            m.contains("agrega a mi pedido") || 
            m.contains("seguir con mi pedido") || 
            m.contains("modifica mi pedido") || 
            m.contains("opcion 1") || 
            m.equals("1")) {
            return "CONTINUAR";
        }

        return "DESCONOCIDO";
    }

    @Async
    public void procesarMensajeConIA(Long chatId, String telefono, String mensajeEntrante) {
        procesarMensajeConIASincrono(chatId, telefono, mensajeEntrante, false);
    }

    public void procesarMensajeConIASincrono(Long chatId, String telefono, String mensajeEntrante) {
        procesarMensajeConIASincrono(chatId, telefono, mensajeEntrante, false);
    }

    public void generarSoloSugerenciaIA(Long chatId, String telefono, String mensajeEntrante) {
        log.info("[OpenAI Service] Generando SOLO SUGERENCIA IA (sin modificar pedidos) para chat ID: {}", chatId);
        procesarMensajeConIASincrono(chatId, telefono, mensajeEntrante, true);
    }

    public void procesarMensajeConIASincrono(Long chatId, String telefono, String mensajeEntrante, boolean soloSugerencia) {
        log.info("[OpenAI Service] Iniciando procesamiento SÍNCRONO para el chat ID: {}, Teléfono: {}, soloSugerencia: {}", chatId, telefono, soloSugerencia);
        
        if (apiKey == null || apiKey.trim().isEmpty() || (apiKey.startsWith("sk-proj-xpb4m") && apiKey.length() < 30)) {
            log.warn("[OpenAI Service] OpenAI API Key no configurada o incompleta. Se omite llamada.");
            return;
        }

        Object lock = com.upc.Util.SharedLockManager.getLock(telefono);
        synchronized (lock) {
            transactionTemplate.execute(status -> {
                boolean esCarritoInicial = false;
                boolean esCompraNatural = false;
                boolean esSolicitudDeProductos = false;
                try {
            // 1. Obtener el chat
            Chat chat = chatRepository.findById(chatId).orElse(null);
            if (chat == null) {
                log.warn("[OpenAI Service] Chat no encontrado para ID: {}", chatId);
                return null;
            }

            // Detección explícita de confirmación de carrito inicial (plantilla web)
            if (mensajeEntrante != null) {
                String mLower = java.text.Normalizer.normalize(
                    mensajeEntrante.toLowerCase().trim(),
                    java.text.Normalizer.Form.NFD
                ).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

                esCarritoInicial = mLower.contains("confirmar mi pedido") ||
                                   mLower.contains("me gustaria confirmar mi pedido") ||
                                   mLower.contains("me gustaria confirmar mi pedido") ||
                                   mLower.contains("confirmar pedido") ||
                                   (mLower.contains("subtotal:") && mLower.contains("total:")) ||
                                   (mLower.startsWith("hola") && mLower.contains("pedido:") && mLower.contains("total:"));

                // Frases naturales de compra (detector Java determinista)
                esCompraNatural = !esCarritoInicial && (
                    mLower.contains("quiero pedir") ||
                    mLower.contains("quisiera pedir") ||
                    mLower.contains("deseo pedir") ||
                    mLower.contains("quiero comprar") ||
                    mLower.contains("quisiera comprar") ||
                    mLower.contains("deseo comprar") ||
                    mLower.contains("me interesa") ||
                    mLower.contains("me gustaria pedir") ||
                    mLower.contains("me gustaria comprar") ||
                    mLower.contains("quiero un ") ||
                    mLower.contains("quiero una ") ||
                    mLower.contains("quiero el ") ||
                    mLower.contains("quiero la ") ||
                    mLower.contains("quiero los ") ||
                    mLower.contains("quiero las ") ||
                    mLower.contains("hola quiero") ||
                    mLower.contains("hola quisiera") ||
                    mLower.contains("me das un") ||
                    mLower.contains("me das una") ||
                    mLower.contains("me puedes dar") ||
                    mLower.contains("quisiera confirmar mi pedido") ||
                    mLower.contains("deseo agregar") ||
                    mLower.contains("quiero agregar") ||
                    mLower.contains("necesito pedir") ||
                    mLower.contains("necesito comprar")
                );

                // Si no detectó frase natural aún, verificar mención a productos del catálogo
                if (!esCarritoInicial && !esCompraNatural) {
                    try {
                        List<Producto> catalogoProductos = productoRepository.findByActivoTrue();
                        for (Producto prod : catalogoProductos) {
                            if (prod.getNombre() != null) {
                                String nomProd = java.text.Normalizer.normalize(
                                    prod.getNombre().toLowerCase(),
                                    java.text.Normalizer.Form.NFD
                                ).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
                                // Usar palabras clave del nombre del producto (> 3 chars)
                                String[] palabrasProd = nomProd.split("[^a-z0-9]+");
                                for (String pal : palabrasProd) {
                                    if (pal.length() > 3 && mLower.contains(pal)) {
                                        esCompraNatural = true;
                                        break;
                                    }
                                }
                            }
                            if (esCompraNatural) break;
                        }
                    } catch (Exception ex) {
                        log.warn("[OpenAI Service] No se pudo verificar catálogo para detección natural: {}", ex.getMessage());
                    }
                }
            }
            esSolicitudDeProductos = esCarritoInicial || esCompraNatural;
            log.info("[OpenAI Service] Detección Java — esCarritoInicial: {}, esCompraNatural: {}", esCarritoInicial, esCompraNatural);

            java.time.ZoneId zoneIdLima = java.time.ZoneId.of("America/Lima");
            LocalDateTime ahoraLima = LocalDateTime.now(zoneIdLima);

            // 2. Obtener los últimos 10 mensajes del historial del chat (memoria conversacional)
            List<Mensaje> historial = mensajeRepository.findTop10ByChatIdOrderByFechaEnvioDesc(chatId);
            // Revertir para que queden en orden cronológico ascendente (el más antiguo primero)
            Collections.reverse(historial);

            Mensaje mensajeActualBD = (!historial.isEmpty()) ? historial.get(historial.size() - 1) : null;
            Long idMensajeActual = mensajeActualBD != null ? mensajeActualBD.getId() : null;

            // CORTE TEMPORAL ESTRICTO POR CICLO DE PEDIDO:
            LocalDateTime fechaCorteChat = chat.getFechaCorte();
            LocalDateTime fechaInicioCiclo = null;
            if (esCarritoInicial) {
                fechaInicioCiclo = (mensajeActualBD != null && mensajeActualBD.getFechaEnvio() != null)
                        ? mensajeActualBD.getFechaEnvio() : ahoraLima;
                chat.setFechaCorte(fechaInicioCiclo);
                chatRepository.save(chat);
                log.info("[OpenAI Service] Nuevo carrito inicial detectado. Establecido fechaInicioCiclo / fechaCorte: {}", fechaInicioCiclo);
            } else if (fechaCorteChat != null) {
                fechaInicioCiclo = fechaCorteChat;
            }

            // PROHIBIDO leer mensajes con timestamp anterior a fechaInicioCiclo
            if (fechaInicioCiclo != null) {
                final LocalDateTime corteFinal = fechaInicioCiclo;
                historial.removeIf(m -> m.getFechaEnvio() != null && m.getFechaEnvio().isBefore(corteFinal));
            }

            // Reordenamiento de historial con logs cronológicos estrictos
            StringBuilder historialStr = new StringBuilder();
            for (Mensaje msg : historial) {
                historialStr.append(msg.getRemitente())
                            .append(": ")
                            .append(msg.getContenido())
                            .append("\n");
            }
            log.info("Historial enviado a OpenAI (tras corte temporal): {}", historialStr.toString());
            List<Mensaje> todosMensajesChat = mensajeRepository.findByChatIdOrderByFechaEnvioAsc(chatId);

            List<Pedido> pedidosCliente = pedidoRepository.findByUsuarioTelefono(telefono);
            StringBuilder contextoPedidos = new StringBuilder();
            contextoPedidos.append("HISTORIAL DE PEDIDOS PREVIOS DEL CLIENTE:\n");
            Pedido pedidoPendienteActivo = null;
            Pedido pedidoRecienCreadoCarrito = null;
            boolean tienePedidoPendiente = false;
            int cantidadPedidosPendientesPrevios = 0;

            if (pedidosCliente != null && !pedidosCliente.isEmpty()) {
                for (Pedido p : pedidosCliente) {
                    if ("PENDIENTE".equalsIgnoreCase(p.getEstado())) {
                        // Calcular fecha de última actividad del cliente previa al mensaje actual acotada al ciclo de vida del pedido p
                        LocalDateTime ultimaActividadAnterior = p.getFecha() != null ? p.getFecha() : ahoraLima;
                        if (p.getFechaPago() != null && p.getFechaPago().isAfter(ultimaActividadAnterior)) {
                            ultimaActividadAnterior = p.getFechaPago();
                        }
                        if (todosMensajesChat != null && !todosMensajesChat.isEmpty()) {
                            for (Mensaje msg : todosMensajesChat) {
                                // Excluir de forma absoluta el mensaje actual que detonó esta llamada por su ID único de BD
                                if (idMensajeActual != null && idMensajeActual.equals(msg.getId())) {
                                    continue;
                                }
                                // Solo considerar mensajes del CLIENTE enviados durante o después de la creación de este pedido p
                                if ("CLIENTE".equalsIgnoreCase(msg.getRemitente()) && msg.getFechaEnvio() != null) {
                                    if (p.getFecha() != null && !msg.getFechaEnvio().isBefore(p.getFecha()) && msg.getFechaEnvio().isAfter(ultimaActividadAnterior)) {
                                        ultimaActividadAnterior = msg.getFechaEnvio();
                                    }
                                }
                            }
                        }

                        long horasInactividad = java.time.Duration.between(ultimaActividadAnterior, ahoraLima).toHours();

                        if (horasInactividad >= 24) {
                            if (!soloSugerencia) {
                                // Marcar pedido como expirado por inactividad (+24h)
                                p.setEstado("RECHAZADO");
                                p.setResultadoConciliacion("PEDIDO EXPIRADO POR INACTIVIDAD (+24 HORAS)");
                                pedidoRepository.save(p);
                                log.info("[PEDIDO EXPIRADO] Pedido ID {} del cliente {} expiró por inactividad previa ({}h). Estado actualizado a RECHAZADO.", p.getId(), telefono, horasInactividad);
                            }

                            contextoPedidos.append("- Pedido ID: ").append(p.getId())
                                           .append(" [PEDIDO ANTERIOR EXPIRADO POR INACTIVIDAD (+24 HORAS) - YA NO ES ACTIVO]\n");
                        } else {
                            long minutosCreacion = p.getFecha() != null ? java.time.Duration.between(p.getFecha(), ahoraLima).toMinutes() : 999;
                            boolean esPedidoRecienCreadoDeEsteCarrito = esCarritoInicial && minutosCreacion <= 10;

                            if (esPedidoRecienCreadoDeEsteCarrito) {
                                pedidoRecienCreadoCarrito = p;
                                contextoPedidos.append("- Pedido ID: ").append(p.getId())
                                               .append(", Fecha: ").append(p.getFecha())
                                               .append(", Estado: ").append(p.getEstado())
                                               .append(", Total: S/.").append(p.getTotal() != null ? p.getTotal() : 0.0)
                                               .append(", Productos: ").append(p.getVariantesEspecificas() != null ? p.getVariantesEspecificas() : "N/A")
                                               .append(" (EN CURSO - CARRITO ACTUAL DE ESTA SESIÓN)\n");
                            } else {
                                cantidadPedidosPendientesPrevios++;
                                if (pedidoPendienteActivo == null) {
                                    pedidoPendienteActivo = p;
                                }
                                contextoPedidos.append("- Pedido ID: ").append(p.getId())
                                               .append(", Fecha: ").append(p.getFecha())
                                               .append(", Estado: ").append(p.getEstado())
                                               .append(", Total: S/.").append(p.getTotal() != null ? p.getTotal() : 0.0)
                                               .append(", Productos: ").append(p.getVariantesEspecificas() != null ? p.getVariantesEspecificas() : "N/A")
                                               .append(" (ACTIVO - Inactividad: ").append(horasInactividad).append("h)\n");
                            }
                        }
                    } else {
                        contextoPedidos.append("- Pedido ID: ").append(p.getId())
                                       .append(", Fecha: ").append(p.getFecha())
                                       .append(", Estado: ").append(p.getEstado())
                                       .append(", Total: S/.").append(p.getTotal() != null ? p.getTotal() : 0.0)
                                       .append(", Productos: ").append(p.getVariantesEspecificas() != null ? p.getVariantesEspecificas() : "N/A")
                                       .append("\n");
                    }
                }
            } else {
                contextoPedidos.append("El cliente no cuenta con pedidos previos registrados.\n");
            }

            // Exclusión explícita del pedido recién creado de este carrito en tienePedidoPendiente:
            // Si el cliente no tiene pedidos pendientes previos, tienePedidoPendiente debe ser FALSE.
            if (cantidadPedidosPendientesPrevios > 0) {
                tienePedidoPendiente = true;
            } else {
                tienePedidoPendiente = false;
                // Si no hay pedidos previos pero sí el pedido de este carrito, mantenemos la referencia para reutilizarlo sin duplicar
                if (pedidoPendienteActivo == null && pedidoRecienCreadoCarrito != null) {
                    pedidoPendienteActivo = pedidoRecienCreadoCarrito;
                }
            }

            if (!tienePedidoPendiente) {
                contextoPedidos.append("\nREGLA CRÍTICA DE BASE DE DATOS: El cliente NO TIENE NINGÚN PEDIDO PENDIENTE PREVIO en BD en este momento. CUALQUIER mención a un pedido pendiente en mensajes antiguos del historial es OBSOLETA. Si existe un pedido en curso, corresponde a este mismo carrito inicial. PROHIBIDO sugerir 'Tienes un pedido pendiente' o preguntar por continuar/nuevo.\n");
            }

            // 3.b Obtener catálogo de productos activos con su stock real en BD para inyectar a la IA
            List<Producto> productosActivos = productoRepository.findByActivoTrue();
            StringBuilder contextoStock = new StringBuilder();
            contextoStock.append("DISPONIBILIDAD REAL DE STOCK EN INVENTARIO (BASE DE DATOS):\n");
            for (Producto prod : productosActivos) {
                int st = prod.getStock() != null ? prod.getStock() : 0;
                contextoStock.append("- ").append(prod.getNombre())
                             .append(" (ID: ").append(prod.getId()).append("): ")
                             .append("Stock disponible = ").append(st)
                             .append(st == 0 ? " [AGOTADO]" : "")
                             .append(", Precio: S/.").append(prod.getPrecio() != null ? prod.getPrecio() : 0.0)
                             .append("\n");
            }

            // 3.c VERIFICACIÓN DETERMINISTA DE FLUJO DE REEMPLAZO DE STOCK Y MANEJO DE CANTIDAD:
            ReemplazoStockResult flujoReemplazo = evaluarFlujoReemplazoStock(
                    chat,
                    pedidoPendienteActivo,
                    mensajeEntrante,
                    historial,
                    idMensajeActual,
                    fechaInicioCiclo,
                    productoRepository.findAll()
            );
            if (flujoReemplazo.manejado) {
                log.info("[OpenAI Service] Flujo de reemplazo de stock detectado para chat ID {}. Respuesta determinista aplicada (se omite llamada a OpenAI y DECISION_PENDIENTE).", chatId);
                chat.setSugerenciaIa(flujoReemplazo.respuestaSugerida);
                chat.setContextoInmediato(flujoReemplazo.nuevoContextoInmediato);
                chat.setPrioridad(flujoReemplazo.prioridad);
                chat.setPedidoIdentificado(flujoReemplazo.pedidoIdentificado);
                chat.setDireccionDetectada(flujoReemplazo.direccionDetectada);
                chat.setDatosCompletos(flujoReemplazo.datosCompletos);
                chat.setFasePedido(flujoReemplazo.fasePedido);
                if (pedidoPendienteActivo != null) {
                    chat.setPedidoReferenciadoId(pedidoPendienteActivo.getId());
                }
                chatRepository.save(chat);
                return null;
            }

            // 4. Construir la consulta a OpenAI
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            String formattedCorpNum = formatNumberCommercial(corporateNumber);

            // System prompt del e-commerce corregido con reglas de flujo obligatorias, número de Yape/Plin corporativo y calibración estricta
            String systemPrompt = "Actúa como el asistente inteligente y experto en ventas de nuestro e-commerce 'Punto y Coma'.\n" +
                    "IMPORTANTE: Operas bajo el modelo de ASISTENCIA HUMANO-IN-THE-LOOP. Tu función es interpretar la conversación, evaluar la disponibilidad real de inventario y proponer una 'respuestaSugerida' para que el administrador la revise y confirme. NUNCA tomas decisiones administrativas autónomas.\n\n";
            
            if ("SOLICITAR_FALTANTE".equals(chat.getContextoInmediato())) {
                systemPrompt += "REGLA DE CONTEXTO DE ACCIÓN INMEDIATA ADMINISTRADOR ACTIVA (SOLICITAR MONTO FALTANTE):\n" +
                        "- El administrador ha determinado que el cliente envió un voucher con un monto menor al total del pedido.\n" +
                        "- IGNORA cualquier saludo genérico, no solicites datos de entrega y no pidas voucher nuevo.\n" +
                        "- Tu 'respuestaSugerida' DEBE ser DIRECTAMENTE un mensaje sumamente educado, empático y comercial redactado para el cliente, solicitándole completar el saldo pendiente de pago para poder proceder con el despacho de su pedido.\n\n";
            }
            
            systemPrompt += "FLUIDO COMERCIAL Y ETAPAS OBLIGATORIAS DE COMPRA POR WHATSAPP:\n\n" +
                    "1. FASE 'INICIAL': Se activa cuando el cliente saluda, consulta productos, indica su pedido o no ha completado los 8 datos obligatorios de entrega.\n" +
                    "   a) INTERPRETA las cantidades solicitadas de cada producto (ej. 'Taza rosa x2 y Taza roja x1').\n" +
                    "   b) CONSULTA OBLIGATORIAMENTE el bloque 'DISPONIBILIDAD REAL DE STOCK EN INVENTARIO (BASE DE DATOS)'. PROHIBIDO inventar stock.\n" +
                    "   c) EN TU 'respuestaSugerida', INFORMA SIEMPRE PRIMERO LA DISPONIBILIDAD DE STOCK:\n" +
                    "      - Si TODO tiene stock: Confirma disponibilidad amablemente (ej. '¡Hola! Contamos con disponibilidad para las 2 Tazas artesanales de arcilla y 1 Taza Nube 😊.').\n" +
                    "      - Si hay STOCK PARCIAL (solicitado > disponible): Informa la cantidad disponible exacta vs solicitada (ej. 'Tenemos disponible 1 Taza rosa de las 2 que solicitaste y sí contamos con la Taza roja. ¿Deseas continuar con las unidades disponibles?').\n" +
                    "      - Si el producto está AGOTADO (stock = 0): Informa con amabilidad que el producto está agotado y ofrece las unidades disponibles o alternativas.\n" +
                    "   d) TRAS INFORMAR LA DISPONIBILIDAD, SOLICITA OBLIGATORIAMENTE LOS DATOS DE ENTREGA EXACTOS:\n" +
                    PLANTILLA_DATOS_ENTREGA + "\n" +
                    "   *PROHIBIDO solicitar métodos de pago o enviar números de Yape/Plin/Transferencia mientras los datos de entrega estén incompletos. NUNCA pases a pago en esta fase.*\n\n" +
                    "2. FASE 'CONCILIACION': Se activa ÚNICAMENTE cuando los datos de entrega (Nombre completo, DNI, Teléfono, Departamento, Provincia, Distrito, Dirección exacta, Referencias) estén COMPLETOS en el historial o en el mensaje actual.\n" +
                    "   - Confirmar que los datos de entrega fueron registrados.\n" +
                    "   - Indicar el monto total exacto del pedido.\n" +
                    "   - En tu 'respuestaSugerida', guiar al cliente para que realice el pago indicando el método que prefiera (Yape, Plin o Transferencia).\n" +
                    "   *REGLA DE PAGO OBLIGATORIA: Si se envían las instrucciones de Yape o Plin, la 'respuestaSugerida' DEBE incluir de forma obligatoria y explícita que el número celular para realizar el abono por Yape o Plin es " + formattedCorpNum + ".*\n" +
                    "   - Solicitar amablemente que envíe la captura o voucher del pago por este chat.\n\n" +
                    "REGLA DE RESPUESTA SUGERIDA Y CONTROL DE DECISIÓN DE PEDIDOS EN LA BD:\n" +
                    " - INTENCIÓN DE PEDIDO (intencionPedido):\n" +
                    "   * 'NUEVO': El cliente indica explícitamente que desea iniciar un pedido nuevo/separado/independiente (ej. 'quiero un pedido nuevo', 'hazme otro pedido', 'quiero comprar aparte', 'opción 2', 'nuevo').\n" +
                    "   * 'CONTINUAR': El cliente indica explícitamente que desea modificar o proceder con su pedido pendiente previo (ej. 'continúa el anterior', 'quiero el pedido anterior', 'agrégalo a mi pedido', 'modifica el anterior', 'opción 1', 'continuar').\n" +
                    "   * 'DECISION_PENDIENTE': Existe un pedido PENDIENTE activo (<24h) y el cliente solicita productos o responde de forma ambigua ('sí', 'ya', 'está bien', etc.) SIN haber aclarado explícitamente si desea continuar el pedido anterior o crear uno nuevo.\n" +
                    "   * 'NINGUNO': No aplica o es una consulta general.\n" +
                    " - REGLA OBLIGATORIA PARA INTENCIÓN CONTINUAR: Cuando intencionPedido sea CONTINUAR, datosPedidoSuperficial.productos debe contener ÚNICAMENTE los productos NUEVOS solicitados en la última interacción del cliente. NO incluyas productos que ya pertenecían al pedido anterior salvo que el cliente haya solicitado agregar más unidades de esos mismos productos.\n" +
                    " - SI existe un PEDIDO ANTERIOR EXPIRADO POR INACTIVIDAD (+24 HORAS), dicho pedido pasó a RECHAZADO y YA NO ES ACTIVO. NO intentes continuar ese pedido ni mezclar sus productos con la nueva solicitud. Procesa la solicitud actual del cliente como un NUEVO PEDIDO independiente.\n" +
                    " - SI el cliente está respondiendo a una oferta de REEMPLAZO por producto agotado (ej. 'Por taza nube', 'cámbialo por...', o indica cantidad tras pregunta de reemplazo), PROHIBIDO asignar 'DECISION_PENDIENTE'. Es una modificación del pedido pendiente activo.\n" +
                    " - SI el estado del pedido es 'PENDIENTE' y el cliente ya envió un voucher ('[VOUCHER]'), la 'respuestaSugerida' DEBE indicar que el comprobante está en proceso de validación por el equipo.\n" +
                    " - SI el estado del pedido es 'VALIDADO', redactarás una confirmación feliz de que el pago fue validado por el administrador y que el pedido se está preparando para su despacho. NO solicitarás dirección, pago ni comprobante nuevamente.\n" +
                    " - SI el estado del pedido es 'RECHAZADO' por un motivo real de comprobante, redactarás un mensaje amable alertando al cliente sobre la discrepancia en el comprobante o pago, solicitándole revisar o enviar nuevamente el comprobante sin reiniciar el pedido comercial.\n\n";
                    
            systemPrompt += "Debes retornar UNICAMENTE un objeto JSON de salida:\n" +
                    "{\n" +
                    "  \"fasePedido\": \"INICIAL\" | \"CONCILIACION\" | \"RECLAMO_CONSULTA\",\n" +
                    "  \"intencionPedido\": \"NUEVO\" | \"CONTINUAR\" | \"DECISION_PENDIENTE\" | \"NINGUNO\",\n" +
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
                    "    \"dni\": string o null,\n" +
                    "    \"telefono\": string o null,\n" +
                    "    \"departamento\": string o null,\n" +
                    "    \"provincia\": string o null,\n" +
                    "    \"distrito\": string o null,\n" +
                    "    \"direccionExacta\": string o null,\n" +
                    "    \"referencia\": string o null,\n" +
                    "    \"direccionEntrega\": string o null,\n" +
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
            
            // 2. System Message de Contexto de Compras, Cliente, Stock Real y Vouchers
            String estadoPedidoPendiente = "NINGUNO";
            String resultadoOcrPendiente = "NINGUNO";
            double totalPedido = 0.0;
            double montoOcr = 0.0;
            double diferenciaMonto = 0.0;
            StringBuilder bloqueVouchers = new StringBuilder();
            String estadoFinancieroBD = "SIN PEDIDO PENDIENTE";

            if (pedidoPendienteActivo != null) {
                estadoPedidoPendiente = pedidoPendienteActivo.getEstado();
                String resBD = pedidoPendienteActivo.getResultadoConciliacion();
                if (resBD != null && (resBD.contains("NUEVO PEDIDO") || resBD.contains("INACTIVIDAD") || resBD.contains("EXPIRADO"))) {
                    resultadoOcrPendiente = "NINGUNO (Cierre comercial/expiración anterior - NO es rechazo de voucher)";
                } else {
                    resultadoOcrPendiente = resBD != null ? resBD : "Sin resultado";
                }
                totalPedido = pedidoPendienteActivo.getTotal() != null ? pedidoPendienteActivo.getTotal() : 0.0;
                Double sumBD = pagoVoucherRepository.obtenerMontoTotalPagadoPorPedido(pedidoPendienteActivo.getId());
                montoOcr = sumBD != null ? sumBD : (pedidoPendienteActivo.getMontoOcr() != null ? pedidoPendienteActivo.getMontoOcr() : 0.0);
                diferenciaMonto = totalPedido - montoOcr;

                List<PagoVoucher> listaVouchers = pagoVoucherRepository.findByPedidoIdOrderByFechaPagoAsc(pedidoPendienteActivo.getId());
                if (listaVouchers != null && !listaVouchers.isEmpty()) {
                    for (int i = 0; i < listaVouchers.size(); i++) {
                        PagoVoucher v = listaVouchers.get(i);
                        bloqueVouchers.append(String.format(" - Voucher %d: S/ %.2f (Ref: %s)\n",
                                i + 1, v.getMonto() != null ? v.getMonto() : 0.0,
                                v.getReferencia() != null ? v.getReferencia() : "Sin ref"));
                    }
                } else {
                    bloqueVouchers.append(" - Ningún voucher registrado aún\n");
                }

                if (Math.abs(diferenciaMonto) < 0.01 || diferenciaMonto <= 0.0) {
                    if (diferenciaMonto < -0.01) {
                        estadoFinancieroBD = "SOBREPAGO (Monto pagado excede por S/ " + String.format("%.2f", Math.abs(diferenciaMonto)) + ")";
                    } else {
                        estadoFinancieroBD = "COMPLETAMENTE PAGADO";
                    }
                } else {
                    estadoFinancieroBD = "PAGO PARCIAL (Saldo pendiente: S/ " + String.format("%.2f", diferenciaMonto) + ")";
                }
            }

            ObjectNode contextMessage = objectMapper.createObjectNode();
            contextMessage.put("role", "system");
            contextMessage.put("content", "INFORMACIÓN DEL CLIENTE Y CONTEXTO DE COMPRAS PREVIAS:\n" +
                    "Nombre: " + chat.getNombreCliente() + "\n" +
                    "Teléfono: " + telefono + "\n" +
                    "¿Tiene pedido pendiente previo activo actualmente?: " + (tienePedidoPendiente ? "SÍ (ID: " + pedidoPendienteActivo.getId() + ")" : "NO") + "\n" +
                    "ESTADO ACTUAL DEL PEDIDO PENDIENTE EN BASE DE DATOS: " + (tienePedidoPendiente ? estadoPedidoPendiente : "NINGUNO") + "\n" +
                    "\nESTADO REAL DE PAGOS:\n" +
                    "Pedido total: S/ " + String.format("%.2f", totalPedido) + "\n" +
                    "Vouchers registrados:\n" + bloqueVouchers.toString() +
                    "Total pagado acumulado: S/ " + String.format("%.2f", montoOcr) + "\n" +
                    "Saldo pendiente: S/ " + String.format("%.2f", diferenciaMonto) + "\n" +
                    "Estado financiero: " + estadoFinancieroBD + "\n\n" +
                    "RESULTADO OCR CONCILIACIÓN DEL PEDIDO PENDIENTE: " + resultadoOcrPendiente + "\n" +
                    "CONTEXTO CONVERSACIONAL DE INTERACCIÓN ANTERIOR: " + (chat.getContextoInmediato() != null ? chat.getContextoInmediato() : "NINGUNO") + "\n\n" +
                    contextoStock.toString() + "\n" +
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
                String cont = msg.getContenido();
                if (cont != null) {
                    if (cont.contains("data:image/") || cont.contains("[VOUCHER]")) {
                        // Si el voucher fue enviado ANTES de la creación del pedido activo actual, etiquetarlo como histórico cerrado
                        boolean esVoucherHistoricoCerrado = false;
                        if (pedidoPendienteActivo != null && pedidoPendienteActivo.getFecha() != null && msg.getFechaEnvio() != null) {
                            if (msg.getFechaEnvio().isBefore(pedidoPendienteActivo.getFecha().minusSeconds(5))) {
                                esVoucherHistoricoCerrado = true;
                            }
                        } else if (pedidoPendienteActivo == null) {
                            esVoucherHistoricoCerrado = true;
                        }

                        if (esVoucherHistoricoCerrado) {
                            cont = "[Comprobante de compra anterior ya cerrada y finalizada - NO aplica para el pedido actual]";
                        } else {
                            cont = "[VOUCHER / Comprobante de pago enviado por el cliente para el pedido actual]";
                        }
                    } else if (cont.length() > 1000) {
                        cont = cont.substring(0, 1000) + "...";
                    }
                }
                msgNode.put("content", cont != null ? cont : "");
                messagesArray.add(msgNode);
            }
            
            requestBody.set("messages", messagesArray);

            // Forzar respuesta en formato JSON
            ObjectNode responseFormat = objectMapper.createObjectNode();
            responseFormat.put("type", "json_object");
            requestBody.set("response_format", responseFormat);

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);

            log.info("[OpenAI Service] Enviando petición a OpenAI con memoria nativa de {} mensajes e inventario real...", historial.size());
            String responseStr = restTemplate.postForObject(apiUrl, entity, String.class);

            if (responseStr != null) {
                JsonNode responseJson = objectMapper.readTree(responseStr);
                String contentResult = responseJson.path("choices").get(0).path("message").path("content").asText();
                log.info("[OpenAI Service] Respuesta recibida de OpenAI: {}", contentResult);

                // Parsear el JSON devuelto por OpenAI
                JsonNode aiResult = objectMapper.readTree(contentResult);
                
                String fasePedido = aiResult.path("fasePedido").asText("INICIAL");
                String intencionOpenAi = aiResult.path("intencionPedido").asText("NINGUNO").trim().toUpperCase();
                JsonNode datosPedidoSuperficial = aiResult.path("datosPedidoSuperficial");
                JsonNode datosConciliacion = aiResult.path("datosConciliacion");

                // CONTROL ESTRUCTURAL DE DIRECCIÓN Y FASE DE PAGO
                String nomComp = getSafeString(datosConciliacion, "nombreCompleto");
                String dniVal = getSafeString(datosConciliacion, "dni");
                String telVal = getSafeString(datosConciliacion, "telefono");
                if (telVal.isEmpty()) telVal = getSafeString(datosConciliacion, "telefonoContacto");
                String depto = getSafeString(datosConciliacion, "departamento");
                String prov = getSafeString(datosConciliacion, "provincia");
                String dist = getSafeString(datosConciliacion, "distrito");
                String dirEx = getSafeString(datosConciliacion, "direccionExacta");
                String ref = getSafeString(datosConciliacion, "referencia");
                if (ref.isEmpty()) ref = getSafeString(datosConciliacion, "referencias");

                boolean tiene8Campos = !nomComp.isEmpty() && !dniVal.isEmpty() && !telVal.isEmpty() &&
                        !depto.isEmpty() && !prov.isEmpty() && !dist.isEmpty() && !dirEx.isEmpty() && !ref.isEmpty();
                boolean tiene5Campos = tiene8Campos || (!depto.isEmpty() && !prov.isEmpty() && !dist.isEmpty() && !dirEx.isEmpty() && !ref.isEmpty());

                boolean direccionEnBD = false;
                if (pedidoPendienteActivo != null) {
                    String dirEnt = pedidoPendienteActivo.getDireccionEntrega();
                    String dirEnv = pedidoPendienteActivo.getDireccionEnvio();
                    if (dirEnt != null && !dirEnt.trim().isEmpty()) {
                        direccionEnBD = true;
                    } else if (dirEnv != null && !dirEnv.trim().isEmpty()) {
                        direccionEnBD = true;
                    }
                }

                boolean mensajeTieneDireccion = mensajeEntrante != null && 
                    (mensajeEntrante.toLowerCase().contains("departamento") || 
                     mensajeEntrante.toLowerCase().contains("distrito") || 
                     mensajeEntrante.toLowerCase().contains("provincia") || 
                     mensajeEntrante.toLowerCase().contains("dirección") || 
                     mensajeEntrante.toLowerCase().contains("dir:"));

                boolean esVoucher = mensajeEntrante != null && (mensajeEntrante.contains("[VOUCHER]") || mensajeEntrante.contains("data:image/") || mensajeEntrante.contains("[DOCUMENTO]"));

                // CORRECCIÓN: pedidoVinculado SOLO puede apuntar a un pedido en estado PENDIENTE o EN REVISION.
                // Pedidos VALIDADO y RECHAZADO están cerrados y NO deben usarse como referencia activa.
                Pedido pedidoVinculado = null;
                if (chat.getPedidoReferenciadoId() != null) {
                    Pedido candidato = pedidoRepository.findById(chat.getPedidoReferenciadoId()).orElse(null);
                    // Solo usar el pedido referenciado si sigue siendo PENDIENTE o EN REVISION
                    if (candidato != null && ("PENDIENTE".equalsIgnoreCase(candidato.getEstado()) || "EN REVISION".equalsIgnoreCase(candidato.getEstado()))) {
                        pedidoVinculado = candidato;
                    } else if (candidato != null) {
                        log.info("[OpenAI Service] pedidoReferenciadoId {} apunta a un pedido en estado '{}' (cerrado/no activo). Se ignora como referencia activa.",
                                chat.getPedidoReferenciadoId(), candidato.getEstado());
                    }
                }
                if (pedidoVinculado == null && pedidoPendienteActivo != null) {
                    pedidoVinculado = pedidoPendienteActivo;
                }
                // El fallback solo busca pedidos PENDIENTE o EN REVISION (< 24h)
                if (pedidoVinculado == null && pedidosCliente != null && !pedidosCliente.isEmpty()) {
                    for (int idx = pedidosCliente.size() - 1; idx >= 0; idx--) {
                        Pedido p = pedidosCliente.get(idx);
                        // IGNORAR completamente pedidos VALIDADO y RECHAZADO
                        if ("PENDIENTE".equalsIgnoreCase(p.getEstado()) || "EN REVISION".equalsIgnoreCase(p.getEstado())) {
                            LocalDateTime f = p.getFecha() != null ? p.getFecha() : ahoraLima;
                            long hrs = java.time.Duration.between(f, ahoraLima).toHours();
                            if (hrs < 24) {
                                pedidoVinculado = p;
                                break;
                            }
                        }
                    }
                }

                boolean ultimoMensajeEsVoucher = false;
                boolean clienteEnvioVoucherReciente = false;

                // REGLA ESTRICTA DE AISLAMIENTO DE VOUCHERS POR PEDIDO:
                // Un comprobante de pago de historial SOLO es válido si existe un pedido activo (PENDIENTE o EN REVISION)
                // y el mensaje del voucher fue enviado DURANTE o DESPUÉS de la creación de dicho pedido activo.
                // Si el mensaje fue enviado antes de la creación del pedido activo (pertenece a un pedido anterior ya VALIDADO/RECHAZADO),
                // o si no existe ningún pedido activo, DEBE SER IGNORADO POR COMPLETO.
                if (pedidoVinculado != null && ("PENDIENTE".equalsIgnoreCase(pedidoVinculado.getEstado()) || "EN REVISION".equalsIgnoreCase(pedidoVinculado.getEstado()))) {
                    LocalDateTime fechaInicioPedido = pedidoVinculado.getFecha() != null ? pedidoVinculado.getFecha().minusSeconds(5) : ahoraLima.minusHours(24);
                    if (historial != null && !historial.isEmpty()) {
                        for (int i = historial.size() - 1; i >= 0; i--) {
                            Mensaje m = historial.get(i);
                            if ("CLIENTE".equalsIgnoreCase(m.getRemitente()) && m.getContenido() != null) {
                                LocalDateTime fMsg = m.getFechaEnvio() != null ? m.getFechaEnvio() : ahoraLima;
                                if (!fMsg.isBefore(fechaInicioPedido)) {
                                    String cont = m.getContenido();
                                    if (cont.contains("[VOUCHER]") || cont.contains("data:image/") || cont.contains("[DOCUMENTO]")) {
                                        clienteEnvioVoucherReciente = true;
                                        if (!ultimoMensajeEsVoucher) {
                                            ultimoMensajeEsVoucher = true;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                boolean tieneVouchersRegistrados = false;
                if (pedidoVinculado != null && ("PENDIENTE".equalsIgnoreCase(pedidoVinculado.getEstado()) || "EN REVISION".equalsIgnoreCase(pedidoVinculado.getEstado())) && !esSolicitudDeProductos) {
                    Double sumVouchers = pagoVoucherRepository.obtenerMontoTotalPagadoPorPedido(pedidoVinculado.getId());
                    if ((sumVouchers != null && sumVouchers > 0.0) ||
                        (pedidoVinculado.getVoucherUrl() != null && !pedidoVinculado.getVoucherUrl().trim().isEmpty()) ||
                        (pedidoVinculado.getMontoOcr() != null && pedidoVinculado.getMontoOcr() > 0.0) ||
                        "PAGO_COMPLETO".equalsIgnoreCase(pedidoVinculado.getEstadoPago())) {
                        tieneVouchersRegistrados = true;
                    }
                }
                boolean contextoEsVoucher = !esSolicitudDeProductos && (esVoucher || ultimoMensajeEsVoucher || clienteEnvioVoucherReciente || tieneVouchersRegistrados);

                // REGLA ESTRICTA DE AISLAMIENTO DE DIRECCIÓN:
                // Si es carrito inicial (creación de 1er pendiente), la dirección siempre es FALSE.
                // Si ya existe un pedido pendiente activo, solo se acepta si ya está en BD o si fue enviada en el mensaje actual
                // o durante el ciclo de vida de este pedido activo.
                boolean tieneDireccionValidaActual = false;
                if (!esCarritoInicial) {
                    if (direccionEnBD) {
                        tieneDireccionValidaActual = true;
                    } else if (tiene5Campos) {
                        if (mensajeTieneDireccion) {
                            tieneDireccionValidaActual = true;
                        } else if (pedidoPendienteActivo != null && historial != null) {
                            LocalDateTime fechaPedido = pedidoPendienteActivo.getFecha() != null ? pedidoPendienteActivo.getFecha().minusSeconds(5) : ahoraLima.minusHours(24);
                            for (Mensaje m : historial) {
                                if ("CLIENTE".equalsIgnoreCase(m.getRemitente()) && m.getFechaEnvio() != null && !m.getFechaEnvio().isBefore(fechaPedido)) {
                                    String cLow = m.getContenido() != null ? m.getContenido().toLowerCase() : "";
                                    if (cLow.contains("departamento") || cLow.contains("distrito") || cLow.contains("provincia") || cLow.contains("dirección") || cLow.contains("dir:")) {
                                        tieneDireccionValidaActual = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }

                boolean direccionCompletaDefinitiva = tieneDireccionValidaActual;
                boolean algunaDireccionDetectada = tieneDireccionValidaActual || (!esCarritoInicial && mensajeTieneDireccion);

                // VALIDACIÓN DETERMINISTA DE INTENCIÓN EN JAVA (SUPERVISA Y SOBRESCRIBE DE FORMA SEGURA A OPENAI)
                String intencionJava = detectarDecisionPedidoExplicita(mensajeEntrante);
                String intencionPedido = intencionOpenAi;
                boolean solicitaProductosNuevos = datosPedidoSuperficial != null && datosPedidoSuperficial.path("productos").isArray() && datosPedidoSuperficial.path("productos").size() > 0;

                // Extraer productos para respuesta de confirmación de carrito
                String textoProductosCarrito = "tu pedido";
                if (datosPedidoSuperficial != null) {
                    JsonNode prodsNode = datosPedidoSuperficial.path("productos");
                    if (prodsNode.isArray() && prodsNode.size() > 0) {
                        StringBuilder sbProds = new StringBuilder();
                        for (int i = 0; i < prodsNode.size(); i++) {
                            JsonNode pn = prodsNode.get(i);
                            String pNom = pn.path("productoNombre").asText("");
                            int pCant = pn.path("cantidad").asInt(1);
                            if (pNom != null && !pNom.trim().isEmpty() && !"null".equalsIgnoreCase(pNom)) {
                                if (sbProds.length() > 0) {
                                    if (i == prodsNode.size() - 1) {
                                        sbProds.append(" y ");
                                    } else {
                                        sbProds.append(", ");
                                    }
                                }
                                sbProds.append(pCant).append(" ").append(pNom.trim());
                            }
                        }
                        if (sbProds.length() > 0) {
                            textoProductosCarrito = sbProds.toString();
                        }
                    }
                }
                if ("tu pedido".equals(textoProductosCarrito) && pedidoPendienteActivo != null && pedidoPendienteActivo.getVariantesEspecificas() != null) {
                    textoProductosCarrito = pedidoPendienteActivo.getVariantesEspecificas();
                }

                // VERIFICACIÓN DETERMINISTA DE STOCK REAL EN BD:
                StockCheckResult stockCheck = verificarStockPedido(datosPedidoSuperficial, mensajeEntrante, chat.getNombreCliente());
                if (stockCheck.isHayStockInsuficiente()) {
                    log.warn("[OpenAI Service] ¡Alerta de Stock! Uno o más productos solicitados no tienen stock suficiente. Forzando advertencia amigable de stock.");
                }

                // CORRECCIÓN — REGLA ESTRICTA CASO A vs CASO B:
                // Si el cliente ya tiene un 1er Pendiente activo en BD y envía una nueva solicitud de productos
                // (carrito web O frase natural), la intención DEBE ser DECISION_PENDIENTE (no NUEVO).
                // Solo si NO tiene ningún pendiente activo, la solicitud de productos crea uno nuevo (CASO A).
                boolean tienePendientePrevioReal = (cantidadPedidosPendientesPrevios >= 1 || (pedidoPendienteActivo != null && "PENDIENTE".equalsIgnoreCase(pedidoPendienteActivo.getEstado())));
                boolean estaRespondiendoDecision = "PREGUNTANDO_DECISION_PEDIDO".equals(chat.getContextoInmediato());

                if ("NUEVO".equalsIgnoreCase(intencionJava)) {
                    intencionPedido = "NUEVO";
                } else if ("CONTINUAR".equalsIgnoreCase(intencionJava)) {
                    intencionPedido = "CONTINUAR";
                } else if (estaRespondiendoDecision) {
                    // Ya se preguntó CONTINUAR/NUEVO, el cliente responde de forma ambigua
                    intencionPedido = "DECISION_PENDIENTE";
                } else if ("STOCK_INSUFICIENTE".equals(chat.getContextoInmediato()) ||
                           (chat.getContextoInmediato() != null && chat.getContextoInmediato().startsWith("ESPERANDO_CANTIDAD")) ||
                           "CONFIRMACION_REEMPLAZO".equals(chat.getContextoInmediato())) {
                    // PROHIBIDO DECISION_PENDIENTE: El cliente está en flujo de reemplazo de stock
                    log.info("[OpenAI Service] Flujo de reemplazo activo en chat ID {}. Se prohíbe DECISION_PENDIENTE.", chatId);
                    intencionPedido = "CONTINUAR";
                } else if (esSolicitudDeProductos && tienePendientePrevioReal && !mensajeTieneDireccion) {
                    // CASO B: Hay un 1er Pendiente activo Y el cliente pide más productos (carrito o frase natural)
                    // => PROHIBIDO crear segundo pedido; preguntar CONTINUAR o NUEVO
                    log.info("[OpenAI Service] CASO B detectado: cliente con 1er Pendiente activo solicita productos nuevamente. Forzando DECISION_PENDIENTE.");
                    intencionPedido = "DECISION_PENDIENTE";
                } else if (esCarritoInicial || esCompraNatural) {
                    // CASO A: No hay pendiente previo => crear nuevo pedido
                    intencionPedido = "NUEVO";
                    chat.setContextoInmediato(null);
                } else {
                    // Consultas generales, mensajes de dirección, etc.
                    if ("DECISION_PENDIENTE".equals(intencionOpenAi)) {
                        intencionPedido = "NINGUNO";
                    }
                }
                log.info("[OpenAI Service] Intención calculada - Java: {}, OpenAI: {}, Final: {}, SolicitaNuevos: {}, EsCarritoInicial: {}, TieneDireccion: {}", 
                        intencionJava, intencionOpenAi, intencionPedido, solicitaProductosNuevos, esCarritoInicial, (mensajeTieneDireccion || tiene5Campos));
                
                boolean pedidoIdentificado = aiResult.path("pedidoIdentificado").asBoolean(false);
                boolean direccionDetectada = aiResult.path("direccionDetectada").asBoolean(false);
                boolean datosCompletos = aiResult.path("datosCompletos").asBoolean(false);
                String prioridad = aiResult.path("prioridad").asText("BAJA");
                
                Long pedidoReferenciadoId = null;
                if (aiResult.has("pedidoReferenciadoId") && !aiResult.path("pedidoReferenciadoId").isNull()) {
                    pedidoReferenciadoId = aiResult.path("pedidoReferenciadoId").asLong();
                }
                String respuestaSugerida = aiResult.path("respuestaSugerida").asText("");

                if (contextoEsVoucher) {
                    fasePedido = "CONCILIACION";
                    respuestaSugerida = "¡Muchas gracias por enviarnos tu comprobante! 🎉 Ya lo tenemos registrado y estamos finalizando la validación de tu pedido para proceder con la entrega.";
                } else if (direccionCompletaDefinitiva) {
                    fasePedido = "CONCILIACION";
                } else {
                    fasePedido = "INICIAL";
                    if (stockCheck.isHayStockInsuficiente()) {
                        chat.setContextoInmediato("STOCK_INSUFICIENTE");
                        prioridad = "ALTA";
                        respuestaSugerida = stockCheck.getMensajeSugerido();
                    } else if (esCarritoInicial) {
                        chat.setContextoInmediato(null);
                        respuestaSugerida = "¡Hola! Contamos con disponibilidad para " + textoProductosCarrito + " 😊. Para continuar, por favor, proporciona los siguientes datos de entrega:\n" +
                                PLANTILLA_DATOS_ENTREGA;
                    } else if (!"SOLICITAR_FALTANTE".equals(chat.getContextoInmediato()) && !"PREGUNTANDO_DECISION_PEDIDO".equals(chat.getContextoInmediato()) && !"STOCK_INSUFICIENTE".equals(chat.getContextoInmediato()) && (respuestaSugerida.contains("Yape") || respuestaSugerida.contains("Plin") || respuestaSugerida.contains(formattedCorpNum))) {
                        respuestaSugerida = "¡Hola " + chat.getNombreCliente() + "! 😊 Hemos evaluado tu pedido y contamos con disponibilidad en nuestro inventario. Para poder cotizar el costo de envío y proceder con el pago, por favor compártenos los siguientes datos de entrega 🚚:\n\n" + PLANTILLA_DATOS_ENTREGA + "\n\n¡Quedamos atentos a tus datos para continuar!";
                    }
                }

                // 5. Aplicar lógica de base de datos e inserción real en Pedido y PedidoDetalle
                if (contextoEsVoucher) {
                    log.info("[OpenAI Service] Mensaje o contexto de comprobante/voucher detectado. Se asegura sugerencia determinista de validación.");
                    fasePedido = "CONCILIACION";
                    chat.setDatosCompletos(true);
                    chat.setContextoInmediato(null);
                    if (pedidoVinculado != null && "VALIDADO".equalsIgnoreCase(pedidoVinculado.getEstado())) {
                        respuestaSugerida = "¡Tu pago ha sido validado con éxito! 🎉 Estamos preparando tu pedido para despacho. Te enviaremos el número de seguimiento en cuanto esté en camino. ¡Muchas gracias por tu compra! ❤️";
                    } else {
                        respuestaSugerida = "¡Muchas gracias por enviarnos tu comprobante! 🎉 Ya lo tenemos registrado y estamos finalizando la validación de tu pedido para proceder con la entrega.";
                    }
                } else if (soloSugerencia) {
                    log.info("[OpenAI Service] Modo soloSugerencia activado (Forzar Regeneración IA). Se omite toda mutación de Pedido y PedidoDetalle.");
                    boolean tienePendienteActivo = (pedidoPendienteActivo != null);
                    if (("DECISION_PENDIENTE".equals(intencionPedido) || "PREGUNTANDO_DECISION_PEDIDO".equals(chat.getContextoInmediato())) && tienePendienteActivo) {
                        double totalAnterior = pedidoPendienteActivo.getTotal() != null ? pedidoPendienteActivo.getTotal() : 0.0;
                        chat.setContextoInmediato("PREGUNTANDO_DECISION_PEDIDO");
                        respuestaSugerida = "🛍️ Tienes un pedido pendiente de S/." + String.format("%.2f", totalAnterior) + ".\n\n" +
                                "Como acabas de solicitar otros productos, elige una opción:\n\n" +
                                "✏️ Escribe *CONTINUAR*\n" +
                                "Para AGREGAR los nuevos productos a tu pedido anterior. Se conservarán los productos que ya tenías y se actualizará el total de tu pedido.\n\n" +
                                "🆕 Escribe *NUEVO*\n" +
                                "Para CREAR UN PEDIDO COMPLETAMENTE NUEVO con los productos que acabas de solicitar. Tu pedido anterior se cerrará y NO se modificará.\n\n" +
                                "👉 Escribe *CONTINUAR* o *NUEVO*.";
                    } else if (stockCheck.isHayStockInsuficiente()) {
                        fasePedido = "INICIAL";
                        chat.setContextoInmediato("STOCK_INSUFICIENTE");
                        prioridad = "ALTA";
                        respuestaSugerida = stockCheck.getMensajeSugerido();
                    } else if (esCarritoInicial) {
                        fasePedido = "INICIAL";
                        chat.setContextoInmediato(null);
                        respuestaSugerida = "¡Hola! Contamos con disponibilidad para " + textoProductosCarrito + " 😊. Para continuar, por favor, proporciona los siguientes datos de entrega:\n" +
                                PLANTILLA_DATOS_ENTREGA;
                    } else if (tienePendienteActivo && direccionCompletaDefinitiva) {
                        double totalRealBD = pedidoPendienteActivo.getTotal() != null ? pedidoPendienteActivo.getTotal() : 0.0;
                        String totalRealStr = String.format("%.2f", totalRealBD);
                        String vars = pedidoPendienteActivo.getVariantesEspecificas() != null ? pedidoPendienteActivo.getVariantesEspecificas() : "Productos de tu pedido";
                        
                        Double sumBD = pagoVoucherRepository.obtenerMontoTotalPagadoPorPedido(pedidoPendienteActivo.getId());
                        double pagado = sumBD != null ? sumBD : (pedidoPendienteActivo.getMontoOcr() != null ? pedidoPendienteActivo.getMontoOcr() : 0.0);
                        double saldo = totalRealBD - pagado;
                        
                        if (contextoEsVoucher) {
                            fasePedido = "CONCILIACION";
                            chat.setDatosCompletos(true);
                            chat.setContextoInmediato(null);
                            respuestaSugerida = "¡Muchas gracias por enviarnos tu comprobante! 🎉 Ya lo tenemos registrado y estamos finalizando la validación de tu pedido para proceder con la entrega.";
                        } else {
                            fasePedido = "INICIAL";
                            chat.setDatosCompletos(true);
                            chat.setContextoInmediato(null);
                            if (saldo <= 0.01 && totalRealBD > 0.0) {
                                // RESPUESTA DETERMINISTA DE PAGO COMPLETO
                                respuestaSugerida = "¡Gracias por tu pago! 🛍️ Hemos recibido y registrado el monto total de S/." + totalRealStr + " (" + vars + ") correspondiente a tu pedido. El pago está completo y será revisado por nuestro equipo para continuar con la preparación y despacho. ¡Muchas gracias por tu compra! ❤️";
                            } else if (saldo > 0.01) {
                                if (pagado > 0.01) {
                                    respuestaSugerida = "¡Hola " + chat.getNombreCliente() + "! 😊 Hemos registrado tu abono de S/." + String.format("%.2f", pagado) + " para tu pedido de " + vars + " (Total: S/." + totalRealStr + ").\n\n" +
                                            "📍 Tus datos de envío ya están registrados.\n" +
                                            "💳 Queda un saldo pendiente de S/." + String.format("%.2f", saldo) + ".\n\n" +
                                            "Por favor, realiza el abono del saldo restante a Yape o Plin al número " + formattedCorpNum + " y envíanos la captura o voucher por aquí para completar tu pedido. ¡Gracias! 🙌";
                                } else {
                                    respuestaSugerida = "¡Hola " + chat.getNombreCliente() + "! 😊 Tu pedido por un total de S/." + totalRealStr + " (" + vars + ") se encuentra registrado.\n\n" +
                                            "📍 Tus datos de envío ya se encuentran registrados en el sistema.\n\n" +
                                            "💳 Para proceder con el despacho, puedes realizar el pago de S/." + totalRealStr + " mediante Yape, Plin o Transferencia al número corporativo " + formattedCorpNum + ".\n\n" +
                                            "📲 Por favor, envíanos la captura o voucher de pago por este chat para verificarlo. ¡Muchas gracias! 😊";
                                }
                            }
                        }
                    }
                } else {
                    boolean tienePendienteActivo = (pedidoPendienteActivo != null);

                    if ("DECISION_PENDIENTE".equals(intencionPedido) && tienePendienteActivo) {
                        log.info("[OpenAI Service] Decisión de pedido pendiente activa para cliente con pedido ID {}. NO se modifica BD ni se eliminan detalles.", pedidoPendienteActivo.getId());
                        chat.setContextoInmediato("PREGUNTANDO_DECISION_PEDIDO");

                        double totalAnterior = pedidoPendienteActivo.getTotal() != null ? pedidoPendienteActivo.getTotal() : 0.0;

                        respuestaSugerida = "🛍️ Tienes un pedido pendiente de S/." + String.format("%.2f", totalAnterior) + ".\n\n" +
                                "Como acabas de solicitar otros productos, elige una opción:\n\n" +
                                "✏️ Escribe *CONTINUAR*\n" +
                                "Para AGREGAR los nuevos productos a tu pedido anterior. Se conservarán los productos que ya tenías y se actualizará el total de tu pedido.\n\n" +
                                "🆕 Escribe *NUEVO*\n" +
                                "Para CREAR UN PEDIDO COMPLETAMENTE NUEVO con los productos que acabas de solicitar. Tu pedido anterior se cerrará y NO se modificará.\n\n" +
                                "👉 Escribe *CONTINUAR* o *NUEVO*.";
                    } else if ("PREGUNTANDO_DECISION_PEDIDO".equals(chat.getContextoInmediato()) && !"NUEVO".equals(intencionPedido) && !"CONTINUAR".equals(intencionPedido) && tienePendienteActivo) {
                        log.info("[OpenAI Service] Mensaje ambiguo recibido durante PREGUNTANDO_DECISION_PEDIDO. NO se crea ni modifica pedido.");
                        double totalAnterior = pedidoPendienteActivo.getTotal() != null ? pedidoPendienteActivo.getTotal() : 0.0;
                        respuestaSugerida = "🛍️ Tienes un pedido pendiente de S/." + String.format("%.2f", totalAnterior) + ".\n\n" +
                                "Como acabas de solicitar otros productos, elige una opción:\n\n" +
                                "✏️ Escribe *CONTINUAR*\n" +
                                "Para AGREGAR los nuevos productos a tu pedido anterior. Se conservarán los productos que ya tenías y se actualizará el total de tu pedido.\n\n" +
                                "🆕 Escribe *NUEVO*\n" +
                                "Para CREAR UN PEDIDO COMPLETAMENTE NUEVO con los productos que acabas de solicitar. Tu pedido anterior se cerrará y NO se modificará.\n\n" +
                                "👉 Escribe *CONTINUAR* o *NUEVO*.";
                    } else {
                        Pedido pedidoExistente = null;
                        boolean esFlujoContinuar = false;

                        if ("NUEVO".equals(intencionPedido)) {
                            if (tienePendienteActivo && pedidoPendienteActivo != null && cantidadPedidosPendientesPrevios > 0) {
                                log.info("[OpenAI Service] Marcando pedido PENDIENTE anterior ID {} como RECHAZADO por creación de nuevo pedido.", pedidoPendienteActivo.getId());
                                pedidoPendienteActivo.setEstado("RECHAZADO");
                                pedidoPendienteActivo.setResultadoConciliacion("RECHAZADO POR CREACIÓN DE NUEVO PEDIDO");
                                pedidoRepository.save(pedidoPendienteActivo);
                            }

                            if (esCarritoInicial && pedidoPendienteActivo != null && cantidadPedidosPendientesPrevios == 0) {
                                log.info("[OpenAI Service] Carrito inicial recibido con pedido PENDIENTE ID {} ya iniciado para este carrito. Reutilizando pedido.", pedidoPendienteActivo.getId());
                                pedidoExistente = pedidoPendienteActivo;
                            } else {
                                pedidoExistente = new Pedido();
                                pedidoExistente.setEstado("PENDIENTE");
                                pedidoExistente.setEstadoPago("SIN_PAGO");
                                pedidoExistente.setMontoDetectado(0.0);
                                pedidoExistente.setDiferencia(0.0);
                                java.time.ZoneId zoneId = java.time.ZoneId.of("America/Lima");
                                pedidoExistente.setFecha(LocalDateTime.now(zoneId));
                                pedidoExistente.setNumeroOrden("WSP-" + System.currentTimeMillis());

                                Usuario cliente = usuarioRepository.findByTelefono(telefono).orElseGet(() -> {
                                    Usuario nuevoCliente = new Usuario();
                                    nuevoCliente.setTelefono(telefono);
                                    nuevoCliente.setNombre(chat.getNombreCliente());
                                    nuevoCliente.setEmail(telefono + "@puntoycoma.com");
                                    nuevoCliente.setPassword("client_pass");
                                    nuevoCliente.setRol("CLIENTE");
                                    nuevoCliente.setActivo(true);
                                    nuevoCliente.setHabilitado(true);
                                    return usuarioRepository.save(nuevoCliente);
                                });
                                pedidoExistente.setUsuario(cliente);
                            }
                            chat.setContextoInmediato(null);
                        } else if ("CONTINUAR".equals(intencionPedido) && tienePendienteActivo) {
                            log.info("[OpenAI Service] Intención CONTINUAR detectada explícitamente. Reutilizando y acumulando en pedido PENDIENTE ID {}.", pedidoPendienteActivo.getId());
                            pedidoExistente = pedidoPendienteActivo;
                            esFlujoContinuar = true;
                            chat.setContextoInmediato("NINGUNO");
                        } else {
                            pedidoExistente = pedidoRepository.findFirstByUsuarioTelefonoAndEstado(telefono, "PENDIENTE");
                            if (pedidoExistente == null && solicitaProductosNuevos) {
                                log.info("[OpenAI Service] Registrando nuevo pedido PENDIENTE para el cliente.");
                                pedidoExistente = new Pedido();
                                pedidoExistente.setEstado("PENDIENTE");
                                pedidoExistente.setEstadoPago("SIN_PAGO");
                                pedidoExistente.setMontoDetectado(0.0);
                                pedidoExistente.setDiferencia(0.0);
                                java.time.ZoneId zoneId = java.time.ZoneId.of("America/Lima");
                                pedidoExistente.setFecha(LocalDateTime.now(zoneId));
                                pedidoExistente.setNumeroOrden("WSP-" + System.currentTimeMillis());

                                Usuario cliente = usuarioRepository.findByTelefono(telefono).orElseGet(() -> {
                                    Usuario nuevoCliente = new Usuario();
                                    nuevoCliente.setTelefono(telefono);
                                    nuevoCliente.setNombre(chat.getNombreCliente());
                                    nuevoCliente.setEmail(telefono + "@puntoycoma.com");
                                    nuevoCliente.setPassword("client_pass");
                                    nuevoCliente.setRol("CLIENTE");
                                    nuevoCliente.setActivo(true);
                                    nuevoCliente.setHabilitado(true);
                                    return usuarioRepository.save(nuevoCliente);
                                });
                                pedidoExistente.setUsuario(cliente);
                            }
                        }

                        if (pedidoExistente != null) {
                            // Parsear y actualizar datos de envío
                            String nombreCompleto = getSafeString(datosConciliacion, "nombreCompleto");
                            String dni = getSafeString(datosConciliacion, "dni");
                            String departamento = getSafeString(datosConciliacion, "departamento");
                            String provincia = getSafeString(datosConciliacion, "provincia");
                            String distrito = getSafeString(datosConciliacion, "distrito");
                            String direccionExacta = getSafeString(datosConciliacion, "direccionExacta");
                            String referencia = getSafeString(datosConciliacion, "referencia");
                            if (referencia.isEmpty()) referencia = getSafeString(datosConciliacion, "referencias");
                            String telefonoContacto = getSafeString(datosConciliacion, "telefonoContacto");
                            if (telefonoContacto.isEmpty()) telefonoContacto = getSafeString(datosConciliacion, "telefono");

                            String direccionUnificada = "Dpto: " + departamento + ", Prov: " + provincia + ", Dist: " + distrito + ", Dir: " + direccionExacta + ", Ref: " + referencia;
                            boolean hasAddress = !departamento.isEmpty() || !provincia.isEmpty() || !distrito.isEmpty() || !direccionExacta.isEmpty() || !referencia.isEmpty();
                            if (!hasAddress) {
                                String directEntrega = getSafeString(datosConciliacion, "direccionEntrega");
                                if (!directEntrega.isEmpty()) {
                                    direccionUnificada = directEntrega;
                                    hasAddress = true;
                                }
                            }

                            // REGLA DE AISLAMIENTO: Si es un pedido nuevo recién creado (no CONTINUAR) y el mensaje actual NO contiene datos de dirección, se desestima cualquier dirección devuelta por OpenAI de historial antiguo
                            if (pedidoExistente.getId() == null && !esFlujoContinuar && !mensajeTieneDireccion) {
                                log.info("[OpenAI Service] Desestimando dirección del historial antiguo para el nuevo pedido.");
                                hasAddress = false;
                            }

                            if (nombreCompleto == null || nombreCompleto.trim().isEmpty() || "Admin Principal".equalsIgnoreCase(nombreCompleto)) {
                                nombreCompleto = (pedidoExistente.getNombreCompleto() != null && !pedidoExistente.getNombreCompleto().trim().isEmpty() && !"Admin Principal".equalsIgnoreCase(pedidoExistente.getNombreCompleto())) 
                                        ? pedidoExistente.getNombreCompleto() : chat.getNombreCliente();
                            }
                            pedidoExistente.setNombreCompleto(nombreCompleto);

                            if (!dni.isEmpty()) {
                                pedidoExistente.setDni(dni);
                            }

                            if (hasAddress) {
                                pedidoExistente.setDireccionEntrega(direccionUnificada);
                                pedidoExistente.setDireccionEnvio(direccionUnificada);
                            }
                            if (!telefonoContacto.isEmpty()) {
                                pedidoExistente.setTelefonoContacto(telefonoContacto);
                            }

                            double totalEsperado = aiResult.path("totalEsperado").asDouble(0.0);
                            if (totalEsperado <= 0.0) {
                                totalEsperado = datosPedidoSuperficial.path("totalEsperado").asDouble(0.0);
                            }
                            if (totalEsperado > 0.0 && !esFlujoContinuar && pedidoExistente.getId() == null) {
                                pedidoExistente.setTotal(totalEsperado);
                            }

                            Pedido pedidoGuardado = pedidoRepository.save(pedidoExistente);
                            // Actualizar la referencia de pedidoPendienteActivo al pedido recién guardado para mantener coherencia en las secciones posteriores
                            pedidoPendienteActivo = pedidoGuardado;

                            if ((hasAddress || direccionCompletaDefinitiva) && !esCarritoInicial) {
                                chat.setContextoInmediato(null);
                                chat.setDatosCompletos(true);
                                if (contextoEsVoucher) {
                                    fasePedido = "CONCILIACION";
                                    respuestaSugerida = "¡Muchas gracias por enviarnos tu comprobante! 🎉 Ya lo tenemos registrado y estamos finalizando la validación de tu pedido para proceder con la entrega.";
                                } else {
                                    fasePedido = "INICIAL";
                                    if (!esFlujoContinuar) {
                                        double totalRealBD = pedidoGuardado.getTotal() != null ? pedidoGuardado.getTotal() : 0.0;
                                        String totalRealStr = String.format("%.2f", totalRealBD);
                                        String vars = pedidoGuardado.getVariantesEspecificas() != null ? pedidoGuardado.getVariantesEspecificas() : "Productos de tu pedido";
                                        
                                        Double sumBD = pagoVoucherRepository.obtenerMontoTotalPagadoPorPedido(pedidoGuardado.getId());
                                        double pagado = sumBD != null ? sumBD : (pedidoGuardado.getMontoOcr() != null ? pedidoGuardado.getMontoOcr() : 0.0);
                                        double saldo = totalRealBD - pagado;

                                        if (saldo <= 0.01 && totalRealBD > 0.0) {
                                            respuestaSugerida = "¡Gracias por tu pago! 🛍️ Hemos recibido y registrado el monto total de S/." + totalRealStr + " (" + vars + ") correspondiente a tu pedido. El pago está completo y será revisado por nuestro equipo para continuar con la preparación y despacho. ¡Muchas gracias por tu compra! ❤️";
                                        } else if (saldo > 0.01) {
                                            if (pagado > 0.01) {
                                                respuestaSugerida = "¡Hola " + chat.getNombreCliente() + "! 😊 Hemos registrado tu abono de S/." + String.format("%.2f", pagado) + " para tu pedido de " + vars + " (Total: S/." + totalRealStr + ").\n\n" +
                                                        "📍 Tus datos de envío ya están registrados.\n" +
                                                        "💳 Queda un saldo pendiente de S/." + String.format("%.2f", saldo) + ".\n\n" +
                                                        "Por favor, realiza el abono del saldo restante a Yape o Plin al número " + formattedCorpNum + " y envíanos la captura o voucher por aquí para completar tu pedido. ¡Gracias! 🙌";
                                            } else {
                                                respuestaSugerida = "¡Hola " + chat.getNombreCliente() + "! 😊 Tu pedido por un total de S/." + totalRealStr + " (" + vars + ") se encuentra registrado.\n\n" +
                                                        "📍 Tus datos de envío ya se encuentran registrados en el sistema.\n\n" +
                                                        "💳 Para proceder con el despacho, puedes realizar el pago de S/." + totalRealStr + " mediante Yape, Plin o Transferencia al número corporativo " + formattedCorpNum + ".\n\n" +
                                                        "📲 Por favor, envíanos la captura o voucher de pago por este chat para verificarlo. ¡Muchas gracias! 😊";
                                            }
                                        }
                                    }
                                }
                            }

                            // REGISTRO Y ACUMULACIÓN DE PEDIDO_DETALLE
                            boolean esMensajeDeDireccion = (mensajeTieneDireccion || tiene5Campos) && !esCarritoInicial;
                            JsonNode productosNode = datosPedidoSuperficial.path("productos");
                            if (!esMensajeDeDireccion && productosNode.isArray() && productosNode.size() > 0) {
                                List<Producto> todosProductos = productoRepository.findAll();

                                if (!esFlujoContinuar && "NUEVO".equals(intencionPedido)) {
                                    // Si es un pedido nuevo recién creado, asegurarse de que empiece limpio
                                    pedidoDetalleRepository.deleteByPedidoId(pedidoGuardado.getId());
                                }

                                // Obtener detalles ya existentes para este pedido
                                List<PedidoDetalle> detallesExistentes = pedidoDetalleRepository.findByPedidoId(pedidoGuardado.getId());

                                // Concatenar el texto de la interacción reciente (mensaje actual + últimos mensajes del cliente en historial)
                                StringBuilder sbTextosInteraccion = new StringBuilder();
                                if (mensajeEntrante != null) sbTextosInteraccion.append(mensajeEntrante).append(" ");
                                if (historial != null && !historial.isEmpty()) {
                                    for (int idx = Math.max(0, historial.size() - 4); idx < historial.size(); idx++) {
                                        Mensaje m = historial.get(idx);
                                        if ("CLIENTE".equalsIgnoreCase(m.getRemitente()) && m.getContenido() != null) {
                                            sbTextosInteraccion.append(m.getContenido()).append(" ");
                                        }
                                    }
                                }
                                String textoInteraccionReciente = sbTextosInteraccion.toString().toLowerCase();

                                for (int i = 0; i < productosNode.size(); i++) {
                                    JsonNode prodNode = productosNode.get(i);
                                    String prodNombre = prodNode.path("productoNombre").asText(null);
                                    int cantidadNueva = prodNode.path("cantidad").asInt(1);
                                    double precioUnitario = prodNode.path("precioUnitario").asDouble(0.0);
                                    if (cantidadNueva <= 0) cantidadNueva = 1;

                                    if (prodNombre != null && !prodNombre.trim().isEmpty() && !"null".equalsIgnoreCase(prodNombre)) {
                                        Producto productoMatcheado = null;
                                        for (Producto p : todosProductos) {
                                            if (p.getNombre() != null && p.getNombre().toLowerCase().contains(prodNombre.toLowerCase())) {
                                                productoMatcheado = p;
                                                break;
                                            }
                                        }
                                        if (productoMatcheado == null && !todosProductos.isEmpty()) {
                                            productoMatcheado = todosProductos.get(0);
                                        }

                                        if (productoMatcheado != null) {
                                            final Long prodIdMatch = productoMatcheado.getId();
                                            PedidoDetalle detalleExistente = null;
                                            if (detallesExistentes != null) {
                                                for (PedidoDetalle d : detallesExistentes) {
                                                    if (d.getProducto() != null && d.getProducto().getId().equals(prodIdMatch)) {
                                                        detalleExistente = d;
                                                        break;
                                                    }
                                                }
                                            }

                                            if (detalleExistente != null && esFlujoContinuar) {
                                                // DOBLE PROTECCIÓN CONTRA DOBLE ACUMULACIÓN:
                                                // Verificar si el producto ya existente en BD fue realmente mencionado en la interacción reciente del cliente,
                                                // para evitar sumar productos que OpenAI devolvió por simple arrastre del contexto del pedido anterior.
                                                boolean fueMencionadoRecientemente = false;
                                                String nombreMatcheadoLower = productoMatcheado.getNombre() != null ? productoMatcheado.getNombre().toLowerCase() : "";
                                                String prodNombreLower = prodNombre != null ? prodNombre.toLowerCase() : "";

                                                String[] palabras = nombreMatcheadoLower.split("[^a-záéíóúñ0-9]+");
                                                for (String pal : palabras) {
                                                    if (pal.length() > 3 && !pal.equals("taza") && !pal.equals("tazas") && !pal.equals("para")) {
                                                        if (textoInteraccionReciente.contains(pal)) {
                                                            fueMencionadoRecientemente = true;
                                                            break;
                                                        }
                                                    }
                                                }
                                                if (!fueMencionadoRecientemente && !nombreMatcheadoLower.isEmpty() && textoInteraccionReciente.contains(nombreMatcheadoLower)) {
                                                    fueMencionadoRecientemente = true;
                                                }
                                                if (!fueMencionadoRecientemente && !prodNombreLower.isEmpty() && textoInteraccionReciente.contains(prodNombreLower)) {
                                                    fueMencionadoRecientemente = true;
                                                }

                                                if (fueMencionadoRecientemente) {
                                                    log.info("[OpenAI Service] Acumulando {} unidades adicionales para el producto existente '{}' (ID {}) en pedido ID {}.",
                                                            cantidadNueva, productoMatcheado.getNombre(), prodIdMatch, pedidoGuardado.getId());
                                                    detalleExistente.setCantidad(detalleExistente.getCantidad() + cantidadNueva);
                                                    double precioFinal = precioUnitario > 0 ? precioUnitario : (productoMatcheado.getPrecio() != null ? productoMatcheado.getPrecio() : 0.0);
                                                    detalleExistente.setPrecioUnitario(precioFinal);
                                                    pedidoDetalleRepository.save(detalleExistente);
                                                } else {
                                                    log.warn("[OpenAI Service] Doble Protección Activa: Se omite la re-acumulación del producto histórico '{}' (ID {}) porque NO fue solicitado nuevamente en el mensaje reciente.",
                                                            productoMatcheado.getNombre(), prodIdMatch);
                                                }
                                            } else {
                                                // Crear un nuevo PedidoDetalle para este producto
                                                PedidoDetalle detalle = new PedidoDetalle();
                                                detalle.setPedido(pedidoGuardado);
                                                detalle.setProducto(productoMatcheado);
                                                detalle.setCantidad(cantidadNueva);
                                                double precioFinal = precioUnitario > 0 ? precioUnitario : (productoMatcheado.getPrecio() != null ? productoMatcheado.getPrecio() : 0.0);
                                                detalle.setPrecioUnitario(precioFinal);
                                                pedidoDetalleRepository.save(detalle);
                                                if (detallesExistentes != null) {
                                                    detallesExistentes.add(detalle);
                                                }
                                            }
                                        }
                                    }
                                }

                                // Recalcular el TOTAL acumulado y las variantes asociadas al pedido a partir de TODOS sus PedidoDetalle en BD
                                List<PedidoDetalle> todosDetallesGuardados = pedidoDetalleRepository.findByPedidoId(pedidoGuardado.getId());
                                double sumaTotales = 0.0;
                                StringBuilder sbVariantes = new StringBuilder();
                                for (PedidoDetalle d : todosDetallesGuardados) {
                                    double pUnit = d.getPrecioUnitario() != null ? d.getPrecioUnitario() : (d.getProducto() != null && d.getProducto().getPrecio() != null ? d.getProducto().getPrecio() : 0.0);
                                    int cant = d.getCantidad() != null ? d.getCantidad() : 1;
                                    sumaTotales += (pUnit * cant);
                                    if (sbVariantes.length() > 0) sbVariantes.append(", ");
                                    sbVariantes.append(d.getProducto() != null ? d.getProducto().getNombre() : "Producto").append(" x").append(cant);
                                }

                                if (sumaTotales > 0.0) {
                                    pedidoGuardado.setTotal(sumaTotales);
                                }
                                if (sbVariantes.length() > 0) {
                                    pedidoGuardado.setVariantesEspecificas(sbVariantes.toString());
                                }
                                pedidoRepository.save(pedidoGuardado);
                                log.info("[DATABASE] PedidoDetalle acumulado/actualizado para Pedido ID: {}. Total acumulado: S/ {}", pedidoGuardado.getId(), sumaTotales);

                                if (esFlujoContinuar && sumaTotales > 0.0) {
                                    double totalParcialOpenAi = aiResult.path("totalEsperado").asDouble(0.0);
                                    if (totalParcialOpenAi <= 0.0) {
                                        totalParcialOpenAi = datosPedidoSuperficial.path("totalEsperado").asDouble(0.0);
                                    }
                                    String totalRealStr = String.format("%.2f", sumaTotales);
                                    if (totalParcialOpenAi > 0.0) {
                                        String totalParcialStr = String.format("%.2f", totalParcialOpenAi);
                                        String totalParcialIntStr = String.format("%.0f", totalParcialOpenAi);
                                        if (respuestaSugerida != null && !respuestaSugerida.isEmpty()) {
                                            respuestaSugerida = respuestaSugerida.replace("S/." + totalParcialStr, "S/." + totalRealStr)
                                                                                 .replace("S/ " + totalParcialStr, "S/ " + totalRealStr)
                                                                                 .replace("S/." + totalParcialIntStr, "S/." + totalRealStr)
                                                                                 .replace("S/ " + totalParcialIntStr, "S/ " + totalRealStr)
                                                                                 .replace("S/" + totalParcialStr, "S/" + totalRealStr)
                                                                                 .replace("S/" + totalParcialIntStr, "S/" + totalRealStr);
                                        }
                                    }
                                    
                                    // VERIFICACIÓN DETERMINISTA DE DIRECCIÓN Y FASE TRAS CONTINUAR:
                                    if (direccionCompletaDefinitiva) {
                                        if (contextoEsVoucher) {
                                            fasePedido = "CONCILIACION";
                                            chat.setDatosCompletos(true);
                                        } else {
                                            fasePedido = "INICIAL";
                                            chat.setDatosCompletos(true);
                                        }
                                        respuestaSugerida = "¡Perfecto! Hemos agregado los nuevos productos a tu pedido anterior. Tu pedido total actualizado es de S/." + totalRealStr + " (" + sbVariantes.toString() + ").\n\n" +
                                                "📍 Tus datos de envío ya se encuentran registrados en el sistema.\n\n" +
                                                "💳 Para proceder con el despacho, puedes realizar el pago de S/." + totalRealStr + " mediante Yape, Plin o Transferencia al número corporativo " + formattedCorpNum + ".\n\n" +
                                                "📲 Por favor, envíanos la captura o voucher de pago por este chat para verificarlo. ¡Muchas gracias! 😊";
                                    } else {
                                        fasePedido = "INICIAL";
                                        if (respuestaSugerida == null || !respuestaSugerida.contains(totalRealStr) || respuestaSugerida.contains(formattedCorpNum)) {
                                            respuestaSugerida = "¡Perfecto! Hemos agregado los nuevos productos a tu pedido anterior. Tu pedido total actualizado es de S/." + totalRealStr + " (" + sbVariantes.toString() + "). Por favor, indícanos tus datos de envío faltantes para finalizar la cotización.";
                                        }
                                    }
                                }
                                if (esCarritoInicial) {
                                    String varsFinal = sbVariantes.length() > 0 ? sbVariantes.toString() : textoProductosCarrito;
                                    fasePedido = "INICIAL";
                                    chat.setContextoInmediato(null);
                                    respuestaSugerida = "¡Hola! Contamos con disponibilidad para " + varsFinal + " 😊. Para continuar, por favor, proporciona los siguientes datos de entrega:\n" +
                                            PLANTILLA_DATOS_ENTREGA;
                                }
                            } else if (esMensajeDeDireccion) {
                                log.info("[OpenAI Service] Mensaje de datos de entrega detectado. Se preservan intactos los productos y el total original del pedido existente ID {}.", pedidoGuardado.getId());
                            }
                            log.info("[DATABASE] ¡ÉXITO! Registro de Pedido PENDIENTE procesado en base de datos.");
                        }
                    }
                }

                // Sanitizar respuesta sugerida si no corresponde a decisión de pedido pendiente
                if (esCarritoInicial || !tienePedidoPendiente || (!"DECISION_PENDIENTE".equals(intencionPedido) && !"PREGUNTANDO_DECISION_PEDIDO".equals(chat.getContextoInmediato()))) {
                    if (respuestaSugerida != null && (respuestaSugerida.contains("Tienes un pedido pendiente") || respuestaSugerida.contains("pedido pendiente de"))) {
                        log.warn("[OpenAI Service] Alerta de desincronización: OpenAI sugirió un mensaje de pedido pendiente por arrastre de historial pero la intención no es de decisión o es carrito inicial. Limpiando sugerencia.");
                        if (esCarritoInicial) {
                            String varsFinal = (pedidoPendienteActivo != null && pedidoPendienteActivo.getVariantesEspecificas() != null) ? pedidoPendienteActivo.getVariantesEspecificas() : textoProductosCarrito;
                            respuestaSugerida = "¡Hola! Contamos con disponibilidad para " + varsFinal + " 😊. Para continuar, por favor, proporciona los siguientes datos de entrega:\n" +
                                    PLANTILLA_DATOS_ENTREGA;
                        } else if (direccionCompletaDefinitiva && pedidoPendienteActivo != null) {
                            double totalRealBD = pedidoPendienteActivo.getTotal() != null ? pedidoPendienteActivo.getTotal() : 0.0;
                            String totalRealStr = String.format("%.2f", totalRealBD);
                            String vars = pedidoPendienteActivo.getVariantesEspecificas() != null ? pedidoPendienteActivo.getVariantesEspecificas() : "Productos de tu pedido";
                            respuestaSugerida = "¡Hola " + chat.getNombreCliente() + "! 😊 Tu pedido por un total de S/." + totalRealStr + " (" + vars + ") se encuentra registrado.\n\n" +
                                    "📍 Tus datos de envío ya se encuentran registrados en el sistema.\n\n" +
                                    "💳 Para proceder con el despacho, puedes realizar el pago de S/." + totalRealStr + " mediante Yape, Plin o Transferencia al número corporativo " + formattedCorpNum + ".\n\n" +
                                    "📲 Por favor, envíanos la captura o voucher de pago por este chat para verificarlo. ¡Muchas gracias! 😊";
                        } else {
                            respuestaSugerida = "¡Hola " + chat.getNombreCliente() + "! 😊 ¿En qué podemos ayudarte el día de hoy? Si deseas realizar una compra, por favor indícanos qué productos te gustaría cotizar.";
                        }
                    }
                }

                // 6. Actualizar la entidad Chat con los resultados semánticos e indicadores coherentes
                boolean finalPedidoId = (datosPedidoSuperficial != null && datosPedidoSuperficial.path("productos").isArray() && datosPedidoSuperficial.path("productos").size() > 0) || pedidoIdentificado || (pedidoPendienteActivo != null);
                boolean finalDireccionDet = algunaDireccionDetectada || direccionDetectada;
                boolean finalDatosComp = finalPedidoId && direccionCompletaDefinitiva;

                // RESETEO LIMPIO DE INDICADORES EN CASO A (1er PENDIENTE NUEVO) O STOCK INSUFICIENTE:
                if (stockCheck.isHayStockInsuficiente()) {
                    finalPedidoId = true;
                    finalDireccionDet = false;
                    finalDatosComp = false;
                    fasePedido = "INICIAL";
                    chat.setContextoInmediato("STOCK_INSUFICIENTE");
                    prioridad = "ALTA";
                    respuestaSugerida = stockCheck.getMensajeSugerido();
                } else if (esCarritoInicial && !"DECISION_PENDIENTE".equals(intencionPedido) && !"PREGUNTANDO_DECISION_PEDIDO".equals(chat.getContextoInmediato())) {
                    finalPedidoId = true;
                    finalDireccionDet = false;
                    finalDatosComp = false;
                    fasePedido = "INICIAL";
                    chat.setContextoInmediato(null);
                }

                chat.setPedidoIdentificado(finalPedidoId);
                chat.setDireccionDetectada(finalDireccionDet);
                chat.setDatosCompletos(finalDatosComp);
                chat.setPrioridad(prioridad);
                Long idRef = (pedidoPendienteActivo != null && ("PENDIENTE".equalsIgnoreCase(pedidoPendienteActivo.getEstado()) || "EN REVISION".equalsIgnoreCase(pedidoPendienteActivo.getEstado()))) 
                        ? pedidoPendienteActivo.getId() : null;
                chat.setPedidoReferenciadoId(idRef);
                
                // CORRECCIÓN — FAILSAFE CORREGIDO (HUMANO-IN-THE-LOOP):
                // PROHIBIDO usar pedidos VALIDADO/RECHAZADO/EN REVISION como base del failsafe
                // cuando el contexto es una nueva solicitud de productos (esSolicitudDeProductos).
                // El failsafe solo aplica sobre pedidoPendienteActivo (que es el pedido recién creado o el previo PENDIENTE).
                Pedido pedidoParaFailsafe = pedidoPendienteActivo; // NUNCA usar pedidoVinculado si está VALIDADO
                if (pedidoParaFailsafe == null && pedidoVinculado != null
                        && "PENDIENTE".equalsIgnoreCase(pedidoVinculado.getEstado())) {
                    // Solo usar pedidoVinculado si es estrictamente PENDIENTE
                    pedidoParaFailsafe = pedidoVinculado;
                }
                if (pedidoParaFailsafe != null) {
                    String est = pedidoParaFailsafe.getEstado();
                    String resConc = pedidoParaFailsafe.getResultadoConciliacion();
                    Double sumVouchers = pagoVoucherRepository.obtenerMontoTotalPagadoPorPedido(pedidoParaFailsafe.getId());
                    double totP = pedidoParaFailsafe.getTotal() != null ? pedidoParaFailsafe.getTotal() : 0.0;
                    double pagP = sumVouchers != null ? sumVouchers : 0.0;

                    if ("PAGO_COMPLETO".equalsIgnoreCase(pedidoParaFailsafe.getEstadoPago()) || (totP > 0.0 && pagP >= (totP - 0.01))) {
                        // Solo sobrescribir con mensaje de pago completo si NO estamos en medio de una nueva solicitud de compra
                        if (!esSolicitudDeProductos) {
                            String varsP = pedidoParaFailsafe.getVariantesEspecificas() != null ? pedidoParaFailsafe.getVariantesEspecificas() : "Productos de tu pedido";
                            respuestaSugerida = "¡Gracias por tu pago! 🛍️ Hemos recibido y registrado el monto total de S/." + String.format("%.2f", totP) + " (" + varsP + ") correspondiente a tu pedido. El pago está completo y será revisado por nuestro equipo para continuar con la preparación y despacho. ¡Muchas gracias por tu compra! ❤️";
                            fasePedido = "CONCILIACION";
                            chat.setDatosCompletos(true);
                        }
                    } else if ("VALIDADO".equalsIgnoreCase(est)) {
                        // CORRECCIÓN CRÍTICA: NUNCA sobrescribir con mensaje VALIDADO cuando el cliente
                        // está haciendo una nueva solicitud de compra. Solo aplica en contextos de voucher.
                        if (!esSolicitudDeProductos && contextoEsVoucher) {
                            respuestaSugerida = "¡Tu pago ha sido validado con éxito! 🎉 Estamos preparando tu pedido para despacho. Te enviaremos el número de seguimiento en cuanto esté en camino. ¡Muchas gracias por tu compra! ❤️";
                            fasePedido = "CONCILIACION";
                            chat.setDatosCompletos(true);
                        }
                    } else if ("RECHAZADO".equalsIgnoreCase(est)) {
                        // SOLAMENTE activar plantilla de comprobante si el mensaje entrante es un voucher O si el rechazo fue por comprobante/OCR real (NO por nuevo pedido ni expiración)
                        boolean esRechazoComercialOExpiracion = resConc != null && (resConc.contains("NUEVO PEDIDO") || resConc.contains("INACTIVIDAD") || resConc.contains("EXPIRADO"));
                        if ((esVoucher || (resConc != null && (resConc.contains("VOUCHER") || resConc.contains("COMPROBANTE") || resConc.contains("MONTO")))) && !esRechazoComercialOExpiracion) {
                            respuestaSugerida = "Hola " + chat.getNombreCliente() + ", lamentablemente hemos detectado una discrepancia en tu comprobante de pago: " + (resConc != null ? resConc : "Los datos no coinciden.") + ". Por favor, envíanos el comprobante correcto o comunícate con nosotros para solucionarlo. 😊";
                            fasePedido = "CONCILIACION";
                        }
                    } else if ("EN REVISION".equalsIgnoreCase(est) || "EN_REVISION".equalsIgnoreCase(est)) {
                        respuestaSugerida = "Hola " + chat.getNombreCliente() + ", tu comprobante de pago se encuentra en revisión por nuestro equipo. En breve te confirmaremos la validación. ¡Gracias por tu paciencia! 😊";
                    }
                }
                
                if (contextoEsVoucher) {
                    fasePedido = "CONCILIACION";
                    chat.setDatosCompletos(true);
                    chat.setContextoInmediato(null);
                    if (pedidoParaFailsafe != null && "VALIDADO".equalsIgnoreCase(pedidoParaFailsafe.getEstado())) {
                        respuestaSugerida = "¡Tu pago ha sido validado con éxito! 🎉 Estamos preparando tu pedido para despacho. Te enviaremos el número de seguimiento en cuanto esté en camino. ¡Muchas gracias por tu compra! ❤️";
                    } else {
                        respuestaSugerida = "¡Muchas gracias por enviarnos tu comprobante! 🎉 Ya lo tenemos registrado y estamos finalizando la validación de tu pedido para proceder con la entrega.";
                    }
                } else if (stockCheck.isHayStockInsuficiente()) {
                    fasePedido = "INICIAL";
                    chat.setContextoInmediato("STOCK_INSUFICIENTE");
                    prioridad = "ALTA";
                    chat.setPedidoIdentificado(true);
                    chat.setDireccionDetectada(false);
                    chat.setDatosCompletos(false);
                    finalPedidoId = true;
                    finalDireccionDet = false;
                    finalDatosComp = false;
                    respuestaSugerida = stockCheck.getMensajeSugerido();
                } else if (esCarritoInicial && !"DECISION_PENDIENTE".equals(intencionPedido) && !"PREGUNTANDO_DECISION_PEDIDO".equals(chat.getContextoInmediato())) {
                    fasePedido = "INICIAL";
                    chat.setContextoInmediato(null);
                    chat.setPedidoIdentificado(true);
                    chat.setDireccionDetectada(false);
                    chat.setDatosCompletos(false);
                    finalPedidoId = true;
                    finalDireccionDet = false;
                    finalDatosComp = false;
                    String varsFinal = (pedidoPendienteActivo != null && pedidoPendienteActivo.getVariantesEspecificas() != null && !pedidoPendienteActivo.getVariantesEspecificas().trim().isEmpty()) 
                            ? pedidoPendienteActivo.getVariantesEspecificas() : textoProductosCarrito;
                    respuestaSugerida = "¡Hola! Contamos con disponibilidad para " + varsFinal + " 😊. Para continuar, por favor, proporciona los siguientes datos de entrega:\n" +
                            PLANTILLA_DATOS_ENTREGA;
                }
                
                if (contextoEsVoucher && (respuestaSugerida == null || 
                    respuestaSugerida.contains("Para proceder con el despacho, puedes realizar el pago") || 
                    respuestaSugerida.contains("puedes realizar el pago de") || 
                    respuestaSugerida.contains("Tienes un pedido pendiente de"))) {
                    log.warn("[OpenAI Service] Failsafe: Reemplazando plantilla de cobro con agradecimiento de comprobante debido a contexto de voucher.");
                    if (pedidoParaFailsafe != null && "VALIDADO".equalsIgnoreCase(pedidoParaFailsafe.getEstado())) {
                        respuestaSugerida = "¡Tu pago ha sido validado con éxito! 🎉 Estamos preparando tu pedido para despacho. Te enviaremos el número de seguimiento en cuanto esté en camino. ¡Muchas gracias por tu compra! ❤️";
                    } else {
                        respuestaSugerida = "¡Muchas gracias por enviarnos tu comprobante! 🎉 Ya lo tenemos registrado y estamos finalizando la validación de tu pedido para proceder con la entrega.";
                    }
                } else if (!contextoEsVoucher && (respuestaSugerida == null || 
                    respuestaSugerida.toLowerCase().contains("comprobante") || 
                    respuestaSugerida.toLowerCase().contains("voucher") || 
                    respuestaSugerida.toLowerCase().contains("recibido tu pago") || 
                    respuestaSugerida.toLowerCase().contains("en validación"))) {
                    log.warn("[OpenAI Service] Failsafe: Limpiando respuesta de comprobante porque no existe voucher para el pedido activo.");
                    if (stockCheck.isHayStockInsuficiente()) {
                        fasePedido = "INICIAL";
                        chat.setContextoInmediato("STOCK_INSUFICIENTE");
                        prioridad = "ALTA";
                        respuestaSugerida = stockCheck.getMensajeSugerido();
                    } else if (esCarritoInicial && !"DECISION_PENDIENTE".equals(intencionPedido) && !"PREGUNTANDO_DECISION_PEDIDO".equals(chat.getContextoInmediato())) {
                        String varsFinal = (pedidoPendienteActivo != null && pedidoPendienteActivo.getVariantesEspecificas() != null && !pedidoPendienteActivo.getVariantesEspecificas().trim().isEmpty()) 
                                ? pedidoPendienteActivo.getVariantesEspecificas() : textoProductosCarrito;
                        respuestaSugerida = "¡Hola! Contamos con disponibilidad para " + varsFinal + " 😊. Para continuar, por favor, proporciona los siguientes datos de entrega:\n" +
                                PLANTILLA_DATOS_ENTREGA;
                    } else if (direccionCompletaDefinitiva && pedidoPendienteActivo != null) {
                        double totalRealBD = pedidoPendienteActivo.getTotal() != null ? pedidoPendienteActivo.getTotal() : 0.0;
                        String totalRealStr = String.format("%.2f", totalRealBD);
                        String vars = pedidoPendienteActivo.getVariantesEspecificas() != null ? pedidoPendienteActivo.getVariantesEspecificas() : "Productos de tu pedido";
                        respuestaSugerida = "¡Hola " + chat.getNombreCliente() + "! 😊 Tu pedido por un total de S/." + totalRealStr + " (" + vars + ") se encuentra registrado.\n\n" +
                                "📍 Tus datos de envío ya se encuentran registrados en el sistema.\n\n" +
                                "💳 Para proceder con el despacho, puedes realizar el pago de S/." + totalRealStr + " mediante Yape, Plin o Transferencia al número corporativo " + formattedCorpNum + ".\n\n" +
                                "📲 Por favor, envíanos la captura o voucher de pago por este chat para verificarlo. ¡Muchas gracias! 😊";
                    }
                }
                
                if (stockCheck.isHayStockInsuficiente()) {
                    respuestaSugerida = stockCheck.getMensajeSugerido();
                    fasePedido = "INICIAL";
                    chat.setContextoInmediato("STOCK_INSUFICIENTE");
                    prioridad = "ALTA";
                    finalPedidoId = true;
                    finalDireccionDet = false;
                    finalDatosComp = false;
                }

                chat.setSugerenciaIa(respuestaSugerida);
                chat.setFasePedido(fasePedido);
                chat.setPedidoIdentificado(finalPedidoId);
                chat.setDireccionDetectada(finalDireccionDet);
                chat.setDatosCompletos(finalDatosComp);
                chat.setPrioridad(prioridad);

                chatRepository.save(chat);
                log.info("[OpenAI Service] Chat ID: {} actualizado en base de datos. Fase: {}, Prioridad: {}, PedidoIdentificado: {}, DireccionDetectada: {}, DatosCompletos: {}", 
                        chatId, fasePedido, prioridad, finalPedidoId, finalDireccionDet, finalDatosComp);
            }

                } catch (Exception e) {
                    log.error("[OpenAI Service] Excepción durante el procesamiento con OpenAI: {}", e.getMessage(), e);
                    // CORRECCIÓN: Fallback determinista en Java para que si OpenAI falla o da timeout,
                    // el pedido PENDIENTE se cree y registre de todos modos con sus productos y se solicite la entrega.
                    try {
                        Chat chatFallback = chatRepository.findById(chatId).orElse(null);
                        if (chatFallback != null && esSolicitudDeProductos) {
                            Pedido pedidoPendiente = pedidoRepository.findFirstByUsuarioTelefonoAndEstado(telefono, "PENDIENTE");
                            if (pedidoPendiente == null) {
                                // CASO A DETERMINISTA: Crear pedido en BD como PENDIENTE
                                Map<Producto, Integer> prodsExtraidos = parsearProductosDeterminista(mensajeEntrante);
                                Pedido nuevoPed = new Pedido();
                                nuevoPed.setEstado("PENDIENTE");
                                nuevoPed.setEstadoPago("SIN_PAGO");
                                nuevoPed.setMontoDetectado(0.0);
                                nuevoPed.setDiferencia(0.0);
                                java.time.ZoneId zoneId = java.time.ZoneId.of("America/Lima");
                                nuevoPed.setFecha(LocalDateTime.now(zoneId));
                                nuevoPed.setNumeroOrden("WSP-" + System.currentTimeMillis());
                                Usuario cliente = usuarioRepository.findByTelefono(telefono).orElseGet(() -> {
                                    Usuario nc = new Usuario();
                                    nc.setTelefono(telefono);
                                    nc.setNombre(chatFallback.getNombreCliente());
                                    nc.setEmail(telefono + "@puntoycoma.com");
                                    nc.setPassword("client_pass");
                                    nc.setRol("CLIENTE");
                                    nc.setActivo(true);
                                    nc.setHabilitado(true);
                                    return usuarioRepository.save(nc);
                                });
                                nuevoPed.setUsuario(cliente);
                                nuevoPed.setNombreCompleto(chatFallback.getNombreCliente());
                                Pedido pedGuardado = pedidoRepository.save(nuevoPed);

                                double sumaTot = 0.0;
                                StringBuilder sbVars = new StringBuilder();
                                for (Map.Entry<Producto, Integer> entry : prodsExtraidos.entrySet()) {
                                    Producto p = entry.getKey();
                                    int cant = entry.getValue();
                                    PedidoDetalle det = new PedidoDetalle();
                                    det.setPedido(pedGuardado);
                                    det.setProducto(p);
                                    det.setCantidad(cant);
                                    double pUnit = p.getPrecio() != null ? p.getPrecio() : 0.0;
                                    det.setPrecioUnitario(pUnit);
                                    pedidoDetalleRepository.save(det);

                                    sumaTot += (pUnit * cant);
                                    if (sbVars.length() > 0) sbVars.append(", ");
                                    sbVars.append(p.getNombre()).append(" x").append(cant);
                                }
                                if (sumaTot > 0.0) pedGuardado.setTotal(sumaTot);
                                if (sbVars.length() > 0) pedGuardado.setVariantesEspecificas(sbVars.toString());
                                pedGuardado = pedidoRepository.save(pedGuardado);

                                String varsTexto = sbVars.length() > 0 ? sbVars.toString() : "tu pedido";
                                StockCheckResult stockCheckFallback = verificarStockPedido(null, mensajeEntrante, chatFallback.getNombreCliente());
                                if (stockCheckFallback.isHayStockInsuficiente()) {
                                    chatFallback.setSugerenciaIa(stockCheckFallback.getMensajeSugerido());
                                    chatFallback.setFasePedido("INICIAL");
                                    chatFallback.setPedidoReferenciadoId(pedGuardado.getId());
                                    chatFallback.setPedidoIdentificado(true);
                                    chatFallback.setDireccionDetectada(false);
                                    chatFallback.setDatosCompletos(false);
                                    chatFallback.setContextoInmediato("STOCK_INSUFICIENTE");
                                    chatFallback.setPrioridad("ALTA");
                                } else {
                                    String sugerenciaFallback = "¡Hola! Contamos con disponibilidad para " + varsTexto + " 😊. Para continuar, por favor, proporciona los siguientes datos de entrega:\n" +
                                            PLANTILLA_DATOS_ENTREGA;
                                    chatFallback.setSugerenciaIa(sugerenciaFallback);
                                    chatFallback.setFasePedido("INICIAL");
                                    chatFallback.setPedidoReferenciadoId(pedGuardado.getId());
                                    chatFallback.setPedidoIdentificado(true);
                                    chatFallback.setDireccionDetectada(false);
                                    chatFallback.setDatosCompletos(false);
                                    chatFallback.setContextoInmediato(null);
                                }
                                chatRepository.save(chatFallback);
                                log.info("[OpenAI Service] Fallback exitoso: Pedido ID {} creado deterministicamente y chat actualizado tras fallo de OpenAI.", pedGuardado.getId());
                            } else {
                                // CASO B o ya existe un pedido:
                                String sugerenciaFallback = "🛍️ Tienes un pedido pendiente de S/." + String.format("%.2f", (pedidoPendiente.getTotal() != null ? pedidoPendiente.getTotal() : 0.0)) + ".\n\n" +
                                        "Como acabas de solicitar otros productos, elige una opción:\n\n" +
                                        "✏️ Escribe *CONTINUAR*\n" +
                                        "Para AGREGAR los nuevos productos a tu pedido anterior. Se conservarán los productos que ya tenías y se actualizará el total de tu pedido.\n\n" +
                                        "🆕 Escribe *NUEVO*\n" +
                                        "Para CREAR UN PEDIDO COMPLETAMENTE NUEVO con los productos que acabas de solicitar. Tu pedido anterior se cerrará y NO se modificará.\n\n" +
                                        "👉 Escribe *CONTINUAR* o *NUEVO*.";
                                chatFallback.setSugerenciaIa(sugerenciaFallback);
                                chatFallback.setContextoInmediato("PREGUNTANDO_DECISION_PEDIDO");
                                chatRepository.save(chatFallback);
                            }
                        }
                    } catch (Exception fallbackEx) {
                        log.error("[OpenAI Service] Error en fallback de excepción: {}", fallbackEx.getMessage());
                    }
                }
                return null;
            });
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
                        chat.setContextoInmediato("SOLICITAR_FALTANTE");
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

    public Map<Producto, Integer> parsearProductosDeterminista(String texto) {
        Map<Producto, Integer> resultado = new LinkedHashMap<>();
        if (texto == null || texto.trim().isEmpty()) return resultado;

        List<Producto> catalogo = productoRepository.findByActivoTrue();
        if (catalogo == null || catalogo.isEmpty()) {
            catalogo = productoRepository.findAll();
        }

        String textoNorm = java.text.Normalizer.normalize(texto.toLowerCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        // 1. Patrón estructurado de carrito: - Nombre (Cantidad: X)
        java.util.regex.Pattern pCarrito = java.util.regex.Pattern.compile("[-*•]?\\s*([^\\n\\r()]+?)\\s*\\(cantidad:\\s*([0-9a-zA-Z]+)\\)", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher mCarrito = pCarrito.matcher(textoNorm);
        while (mCarrito.find()) {
            String prodStr = mCarrito.group(1).trim();
            int cant = 1;
            Integer parsedCant = parsearNumeroOPalabra(mCarrito.group(2).trim());
            if (parsedCant != null && parsedCant > 0) cant = parsedCant;

            Producto matcheado = buscarProductoMasCercano(prodStr, catalogo);
            if (matcheado != null) {
                resultado.put(matcheado, resultado.getOrDefault(matcheado, 0) + cant);
            }
        }

        // 2. Si no encontró por patrón estructurado, buscar coincidencias con el catálogo
        if (resultado.isEmpty()) {
            for (Producto p : catalogo) {
                if (p.getNombre() == null) continue;
                String nomNorm = java.text.Normalizer.normalize(p.getNombre().toLowerCase(), java.text.Normalizer.Form.NFD)
                        .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
                String[] palabras = nomNorm.split("[^a-z0-9]+");
                boolean match = false;
                for (String pal : palabras) {
                    if (pal.length() > 3 && textoNorm.contains(pal)) {
                        match = true;
                        break;
                    }
                }
                if (match) {
                    int cant = 1;
                    java.util.regex.Pattern pCant = java.util.regex.Pattern.compile("(\\d+|un|uno|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez)\\s*(?:unidades?|de)?\\s*" + java.util.regex.Pattern.quote(p.getNombre().toLowerCase()));
                    java.util.regex.Matcher mCant = pCant.matcher(textoNorm);
                    if (mCant.find()) {
                        Integer parsedCant = parsearNumeroOPalabra(mCant.group(1));
                        if (parsedCant != null && parsedCant > 0) cant = parsedCant;
                    }
                    resultado.put(p, cant);
                }
            }
        }
        return resultado;
    }

    private Producto buscarProductoMasCercano(String query, List<Producto> catalogo) {
        if (query == null || catalogo == null || catalogo.isEmpty()) return null;
        String qNorm = java.text.Normalizer.normalize(query.toLowerCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "").trim();
        for (Producto p : catalogo) {
            if (p.getNombre() == null) continue;
            String pNorm = java.text.Normalizer.normalize(p.getNombre().toLowerCase(), java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{InCombiningDiacriticalMarks}+", "").trim();
            if (pNorm.contains(qNorm) || qNorm.contains(pNorm)) {
                return p;
            }
        }
        for (Producto p : catalogo) {
            if (p.getNombre() == null) continue;
            String pNorm = java.text.Normalizer.normalize(p.getNombre().toLowerCase(), java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{InCombiningDiacriticalMarks}+", "").trim();
            String[] words = qNorm.split("[^a-z0-9]+");
            for (String w : words) {
                if (w.length() > 3 && pNorm.contains(w)) {
                    return p;
                }
            }
        }
        return null;
    }

    public static class ItemStockCheck {
        private String nombreSolicitado;
        private Producto productoBD;
        private int cantidadSolicitada;
        private int stockDisponible;
        private boolean sinStock;
        private boolean stockInsuficiente;

        public ItemStockCheck(String nombreSolicitado, Producto productoBD, int cantidadSolicitada, int stockDisponible) {
            this.nombreSolicitado = nombreSolicitado;
            this.productoBD = productoBD;
            this.cantidadSolicitada = cantidadSolicitada;
            this.stockDisponible = stockDisponible;
            this.sinStock = stockDisponible <= 0;
            this.stockInsuficiente = stockDisponible < cantidadSolicitada;
        }

        public String getNombreSolicitado() { return nombreSolicitado; }
        public Producto getProductoBD() { return productoBD; }
        public int getCantidadSolicitada() { return cantidadSolicitada; }
        public int getStockDisponible() { return stockDisponible; }
        public boolean isSinStock() { return sinStock; }
        public boolean isStockInsuficiente() { return stockInsuficiente; }
    }

    public static class StockCheckResult {
        private boolean hayStockInsuficiente = false;
        private List<ItemStockCheck> itemsSinStock = new ArrayList<>();
        private List<ItemStockCheck> itemsConStock = new ArrayList<>();
        private String mensajeSugerido;

        public boolean isHayStockInsuficiente() { return hayStockInsuficiente; }
        public void setHayStockInsuficiente(boolean hayStockInsuficiente) { this.hayStockInsuficiente = hayStockInsuficiente; }
        public List<ItemStockCheck> getItemsSinStock() { return itemsSinStock; }
        public List<ItemStockCheck> getItemsConStock() { return itemsConStock; }
        public String getMensajeSugerido() { return mensajeSugerido; }
        public void setMensajeSugerido(String mensajeSugerido) { this.mensajeSugerido = mensajeSugerido; }
    }

    public StockCheckResult verificarStockPedido(JsonNode datosPedidoSuperficial, String mensajeEntrante, String nombreCliente) {
        StockCheckResult result = new StockCheckResult();
        List<Producto> catalogo = productoRepository.findAll();
        Map<Long, Integer> cantidadesPorProductoId = new LinkedHashMap<>();
        Map<Long, String> nombresPorProductoId = new LinkedHashMap<>();

        // 1. Extraer del JSON de OpenAI (datosPedidoSuperficial)
        if (datosPedidoSuperficial != null) {
            JsonNode prodsNode = datosPedidoSuperficial.path("productos");
            if (prodsNode.isArray()) {
                for (int i = 0; i < prodsNode.size(); i++) {
                    JsonNode pn = prodsNode.get(i);
                    String prodNom = pn.path("productoNombre").asText(null);
                    int cant = pn.path("cantidad").asInt(1);
                    if (cant <= 0) cant = 1;

                    if (prodNom != null && !prodNom.trim().isEmpty() && !"null".equalsIgnoreCase(prodNom)) {
                        Producto match = buscarProductoMasCercano(prodNom, catalogo);
                        if (match != null) {
                            cantidadesPorProductoId.put(match.getId(), Math.max(cantidadesPorProductoId.getOrDefault(match.getId(), 0), cant));
                            nombresPorProductoId.put(match.getId(), prodNom.trim());
                        }
                    }
                }
            }
        }

        // 2. Extraer determinísticamente del mensaje (para asegurar que no se omita ningún producto del carrito web o texto)
        Map<Producto, Integer> prodsDeterministas = parsearProductosDeterminista(mensajeEntrante);
        if (prodsDeterministas != null) {
            for (Map.Entry<Producto, Integer> entry : prodsDeterministas.entrySet()) {
                Producto p = entry.getKey();
                int cant = entry.getValue();
                if (p != null && p.getId() != null) {
                    cantidadesPorProductoId.put(p.getId(), Math.max(cantidadesPorProductoId.getOrDefault(p.getId(), 0), cant));
                    if (!nombresPorProductoId.containsKey(p.getId())) {
                        nombresPorProductoId.put(p.getId(), p.getNombre());
                    }
                }
            }
        }

        if (cantidadesPorProductoId.isEmpty()) {
            return result;
        }

        // 3. Cruzar con base de datos real
        for (Map.Entry<Long, Integer> entry : cantidadesPorProductoId.entrySet()) {
            Long prodId = entry.getKey();
            int cantSolicitada = entry.getValue();
            Producto pBD = productoRepository.findById(prodId).orElse(null);
            if (pBD != null) {
                int stockDisp = pBD.getStock() != null ? pBD.getStock() : 0;
                String nomSolicitado = nombresPorProductoId.getOrDefault(prodId, pBD.getNombre());
                ItemStockCheck item = new ItemStockCheck(nomSolicitado, pBD, cantSolicitada, stockDisp);

                if (stockDisp <= 0 || stockDisp < cantSolicitada) {
                    result.setHayStockInsuficiente(true);
                    result.getItemsSinStock().add(item);
                } else {
                    result.getItemsConStock().add(item);
                }
            }
        }

        // 4. Formatear mensaje sugerido si falta stock
        if (result.isHayStockInsuficiente()) {
            String saludo = (nombreCliente != null && !nombreCliente.trim().isEmpty() && !"Cliente".equalsIgnoreCase(nombreCliente.trim()))
                    ? "¡Hola " + nombreCliente.trim() + "! "
                    : "¡Hola! ";

            if (result.getItemsSinStock().size() == 1) {
                ItemStockCheck item = result.getItemsSinStock().get(0);
                String nomP = item.getProductoBD() != null ? item.getProductoBD().getNombre() : item.getNombreSolicitado();
                int st = item.getStockDisponible();
                if (st <= 0) {
                    result.setMensajeSugerido(saludo + "Te informamos que el producto " + nomP + " se encuentra temporalmente agotado (Stock disponible: 0). ¿Te gustaría reemplazarlo por otro producto de nuestro catálogo o avisarte apenas tengamos reposición? 😊");
                } else {
                    result.setMensajeSugerido(saludo + "Te informamos que para el producto " + nomP + " solo contamos con " + st + " unidad(es) disponible(s) (solicitaste " + item.getCantidadSolicitada() + "). ¿Te gustaría continuar con las unidades disponibles o reemplazarlo por otro producto de nuestro catálogo? 😊");
                }
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append(saludo).append("Te informamos que los siguientes productos no cuentan con stock suficiente en este momento:\n");
                for (ItemStockCheck item : result.getItemsSinStock()) {
                    String nomP = item.getProductoBD() != null ? item.getProductoBD().getNombre() : item.getNombreSolicitado();
                    int st = item.getStockDisponible();
                    if (st <= 0) {
                        sb.append("- ").append(nomP).append(" (Agotado - Stock disponible: 0)\n");
                    } else {
                        sb.append("- ").append(nomP).append(" (Stock disponible: ").append(st).append(" de ").append(item.getCantidadSolicitada()).append(" solicitadas)\n");
                    }
                }
                sb.append("¿Te gustaría reemplazarlos por otros productos de nuestro catálogo o avisarte apenas tengamos reposición? 😊");
                result.setMensajeSugerido(sb.toString());
            }
        }

        return result;
    }

    public static class ReemplazoStockResult {
        public boolean manejado = false;
        public String respuestaSugerida;
        public String nuevoContextoInmediato;
        public String prioridad = "ALTA";
        public boolean pedidoIdentificado = true;
        public boolean direccionDetectada = false;
        public boolean datosCompletos = false;
        public String fasePedido = "INICIAL";
    }

    public Producto buscarProductoEnTexto(String texto, List<Producto> catalogo) {
        if (texto == null || catalogo == null || catalogo.isEmpty()) return null;
        String tNorm = java.text.Normalizer.normalize(texto.toLowerCase().trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        // 1. Coincidencia directa completa con nombre de producto
        for (Producto p : catalogo) {
            if (p.getNombre() == null) continue;
            String pNorm = java.text.Normalizer.normalize(p.getNombre().toLowerCase().trim(), java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
            if (tNorm.contains(pNorm)) {
                return p;
            }
        }

        // 2. Coincidencia por palabras clave significativas (> 3 letras, excluyendo palabras genéricas)
        for (Producto p : catalogo) {
            if (p.getNombre() == null) continue;
            String pNorm = java.text.Normalizer.normalize(p.getNombre().toLowerCase().trim(), java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
            String[] palabras = pNorm.split("[^a-z0-9]+");
            for (String pal : palabras) {
                if (pal.length() > 3 && !pal.equals("para") && !pal.equals("juego") && !pal.equals("tazas") && !pal.equals("taza")) {
                    if (tNorm.matches(".*\\b" + pal + "\\b.*") || tNorm.contains(pal)) {
                        return p;
                    }
                }
            }
        }

        // 3. Fallback a buscarProductoMasCercano
        return buscarProductoMasCercano(texto, catalogo);
    }

    public Integer extraerCantidad(String texto) {
        if (texto == null || texto.trim().isEmpty()) return null;
        String tNorm = java.text.Normalizer.normalize(texto.toLowerCase().trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        // 1. Si el texto es puramente un número entero (ej. "1", "2")
        if (tNorm.matches("^\\d+$")) {
            try {
                int c = Integer.parseInt(tNorm);
                if (c > 0) return c;
            } catch (Exception ignored) {}
        }

        // 2. Prefijos de cantidad comunes ("solo", "solamente", "dame", "quiero", "por", "seria") + número/palabra
        java.util.regex.Matcher mPrefijo = java.util.regex.Pattern.compile(
                "^(?:solo|solamente|dame|quiero|por|seria|serian|traeme|llevar|llevare)?\\s*(\\d+|un|uno|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez)(?:\\s+(?:unidad|unidades|und|unds|ud|uds|taza|tazas|pieza|piezas|vaso|vasos|producto|productos))?\\s*$"
        ).matcher(tNorm);
        if (mPrefijo.find()) {
            Integer val = parsearNumeroOPalabra(mPrefijo.group(1));
            if (val != null && val > 0) return val;
        }

        // 3. Números con dígitos (ej. "1", "2", "x1", "x 2", "3 unidades", "1 taza", "1 und", "x2", "cantidad: 1")
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:^|\\s+|x\\s*|cantidad:\\s*)(\\d{1,2})(?:\\s+|$)").matcher(tNorm);
        if (m.find()) {
            try {
                int c = Integer.parseInt(m.group(1));
                if (c > 0) return c;
            } catch (Exception ignored) {}
        }

        // 4. Palabras numéricas en español en cualquier contexto
        java.util.regex.Matcher mPal = java.util.regex.Pattern.compile(
                "\\b(solo\\s+un|solo\\s+una|solo\\s+uno|solamente\\s+un|solamente\\s+una|solamente\\s+uno|un|una|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez)\\s*(?:unidades?|unds?|uds?|piezas?)?\\b"
        ).matcher(tNorm);
        if (mPal.find()) {
            String g = mPal.group(1);
            if (g.contains("un") || g.contains("una") || g.contains("uno")) return 1;
            Integer val = parsearNumeroOPalabra(g);
            if (val != null && val > 0) return val;
        }

        return null;
    }

    private boolean esMensajeAfirmativo(String texto) {
        if (texto == null) return false;
        String t = java.text.Normalizer.normalize(texto.toLowerCase().trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return t.equals("si") || t.equals("sí") || t.startsWith("si ") || t.startsWith("sí ") ||
               t.contains("conforme") || t.contains("de acuerdo") || t.contains("perfecto") ||
               t.contains("correcto") || t.contains("dale") || t.equals("ok") || t.startsWith("ok ") ||
               t.contains("continuar") || t.contains("continua");
    }

    public ReemplazoStockResult evaluarFlujoReemplazoStock(
            Chat chat,
            Pedido pedidoPendienteActivo,
            String mensajeEntrante,
            List<Mensaje> historial,
            Long idMensajeActual,
            LocalDateTime fechaCorte,
            List<Producto> catalogo) {

        ReemplazoStockResult res = new ReemplazoStockResult();
        if (pedidoPendienteActivo == null || !"PENDIENTE".equalsIgnoreCase(pedidoPendienteActivo.getEstado())) {
            return res;
        }

        // 1. Determinar si el pedido pendiente tiene algún producto con stock insuficiente
        List<PedidoDetalle> detallesActuales = pedidoDetalleRepository.findByPedidoId(pedidoPendienteActivo.getId());
        boolean pedidoTieneProdAgotado = false;
        if (detallesActuales != null) {
            for (PedidoDetalle d : detallesActuales) {
                if (d.getProducto() != null) {
                    int st = d.getProducto().getStock() != null ? d.getProducto().getStock() : 0;
                    int cant = d.getCantidad() != null ? d.getCantidad() : 1;
                    if (st <= 0 || st < cant) {
                        pedidoTieneProdAgotado = true;
                        break;
                    }
                }
            }
        }

        String ctx = chat.getContextoInmediato();
        boolean contextoEsReemplazo = "STOCK_INSUFICIENTE".equals(ctx) ||
                                      (ctx != null && ctx.startsWith("ESPERANDO_CANTIDAD")) ||
                                      "CONFIRMACION_REEMPLAZO".equals(ctx) ||
                                      "PREGUNTANDO_DECISION_PEDIDO".equals(ctx);

        // 2. Revisar historial: si el administrador/IA ofreció reemplazo o preguntó cantidad
        boolean ultimoAdminOfrecioReemplazoOPregunto = false;
        if (historial != null && !historial.isEmpty()) {
            for (int i = historial.size() - 1; i >= 0; i--) {
                Mensaje m = historial.get(i);
                if (idMensajeActual != null && idMensajeActual.equals(m.getId())) continue;
                if (fechaCorte != null && m.getFechaEnvio() != null && m.getFechaEnvio().isBefore(fechaCorte)) continue;
                String r = m.getRemitente() != null ? m.getRemitente().toUpperCase() : "";
                if (r.contains("ADMIN") || "BOT".equals(r) || "SISTEMA".equals(r)) {
                    String c = m.getContenido() != null ? m.getContenido().toLowerCase() : "";
                    if (c.contains("agotado") || c.contains("reemplazarlo") || c.contains("reemplazar") ||
                        c.contains("reposición") || c.contains("reposicion") ||
                        c.contains("cuántas") || c.contains("cuantas") || c.contains("unidades") ||
                        c.contains("disponible") || c.contains("stock")) {
                        ultimoAdminOfrecioReemplazoOPregunto = true;
                        break;
                    }
                }
            }
        }

        boolean esMensajeReemplazoExplicito = false;
        if (mensajeEntrante != null) {
            String mLow = mensajeEntrante.toLowerCase().trim();
            esMensajeReemplazoExplicito = mLow.startsWith("por ") || mLow.contains("cambiar por") ||
                                         mLow.contains("cambialo por") || mLow.contains("en vez de") ||
                                         mLow.contains("reemplazar") || mLow.contains("reemplazo") ||
                                         mLow.startsWith("me das ") || mLow.startsWith("dame ") ||
                                         mLow.startsWith("quiero ");
        }

        boolean esFlujo = (pedidoTieneProdAgotado && (contextoEsReemplazo || ultimoAdminOfrecioReemplazoOPregunto || esMensajeReemplazoExplicito)) ||
                          (ctx != null && ctx.startsWith("ESPERANDO_CANTIDAD")) ||
                          "CONFIRMACION_REEMPLAZO".equals(ctx);

        if (!esFlujo) {
            return res;
        }

        // FLUJO DE REEMPLAZO ACTIVO:
        res.manejado = true;

        // Sub-caso A: Confirmación de desglose previo
        if ("CONFIRMACION_REEMPLAZO".equals(ctx)) {
            if (esMensajeAfirmativo(mensajeEntrante)) {
                res.respuestaSugerida = "¡Excelente! Por favor, proporciona los siguientes datos de entrega:\n" +
                        PLANTILLA_DATOS_ENTREGA;
                res.nuevoContextoInmediato = null;
                res.prioridad = "ALTA";
                return res;
            }
        }

        // Extraer producto y cantidad
        Producto prodMencionado = buscarProductoEnTexto(mensajeEntrante, catalogo);
        Integer cantidadMencionada = extraerCantidad(mensajeEntrante);

        // Si el cliente no mencionó el producto en este mensaje (ej. solo dijo "1" o "solo una"):
        if (prodMencionado == null) {
            if (ctx != null && ctx.startsWith("ESPERANDO_CANTIDAD:")) {
                try {
                    Long pid = Long.parseLong(ctx.substring("ESPERANDO_CANTIDAD:".length()));
                    prodMencionado = productoRepository.findById(pid).orElse(null);
                } catch (Exception ignored) {}
            }
            if (prodMencionado == null && historial != null) {
                // Buscar en mensajes recientes anteriores (del cliente o administrador) respetando fechaCorte
                for (int i = historial.size() - 1; i >= 0; i--) {
                    Mensaje m = historial.get(i);
                    if (idMensajeActual != null && idMensajeActual.equals(m.getId())) continue;
                    if (fechaCorte != null && m.getFechaEnvio() != null && m.getFechaEnvio().isBefore(fechaCorte)) continue;
                    Producto pHist = buscarProductoEnTexto(m.getContenido(), catalogo);
                    if (pHist != null) {
                        prodMencionado = pHist;
                        break;
                    }
                }
            }
        }

        // Sub-caso C: Se identificó el producto pero NO la cantidad (ej. "Por taza nube")
        if (prodMencionado != null && (cantidadMencionada == null || cantidadMencionada <= 0)) {
            int stockDisp = prodMencionado.getStock() != null ? prodMencionado.getStock() : 0;
            if (stockDisp <= 0) {
                res.respuestaSugerida = "¡Hola " + (chat.getNombreCliente() != null ? chat.getNombreCliente() : "") + "! El producto " + prodMencionado.getNombre() + " también se encuentra temporalmente agotado. ¿Te gustaría consultar por otro producto de nuestro catálogo? 😊";
                res.nuevoContextoInmediato = "STOCK_INSUFICIENTE";
                res.prioridad = "ALTA";
            } else {
                res.respuestaSugerida = "¡Excelente elección! La " + prodMencionado.getNombre() + " tiene un precio de S/ " + String.format("%.2f", prodMencionado.getPrecio()) + " y tenemos stock disponible. ¿Cuántas unidades te gustaría llevar? 😊";
                res.nuevoContextoInmediato = "ESPERANDO_CANTIDAD:" + prodMencionado.getId();
                res.prioridad = "ALTA";
            }
            return res;
        }

        // Sub-caso D: Se tiene producto Y cantidad definida (ej. "Por 1 taza nube" o responde "1")
        if (prodMencionado != null && cantidadMencionada != null && cantidadMencionada > 0) {
            int stockDisp = prodMencionado.getStock() != null ? prodMencionado.getStock() : 0;
            if (stockDisp < cantidadMencionada) {
                if (stockDisp <= 0) {
                    res.respuestaSugerida = "¡Hola! Te informamos que el producto " + prodMencionado.getNombre() + " se encuentra temporalmente agotado. ¿Deseas elegir otro producto? 😊";
                } else {
                    res.respuestaSugerida = "Para el producto " + prodMencionado.getNombre() + " solo contamos con " + stockDisp + " unidad(es) disponible(s). ¿Te gustaría llevar esa cantidad o elegir otro producto? 😊";
                }
                res.nuevoContextoInmediato = "STOCK_INSUFICIENTE";
                res.prioridad = "ALTA";
                return res;
            }

            // Aplicar reemplazo en la base de datos para pedidoPendienteActivo:
            PedidoDetalle detalleAReemplazar = null;
            PedidoDetalle detalleYaExiste = null;
            if (detallesActuales != null) {
                for (PedidoDetalle d : detallesActuales) {
                    if (d.getProducto() != null) {
                        if (d.getProducto().getId().equals(prodMencionado.getId())) {
                            detalleYaExiste = d;
                        }
                        int st = d.getProducto().getStock() != null ? d.getProducto().getStock() : 0;
                        int c = d.getCantidad() != null ? d.getCantidad() : 1;
                        if (st <= 0 || st < c) {
                            detalleAReemplazar = d;
                        }
                    }
                }
            }

            if (detalleAReemplazar != null) {
                if (detalleYaExiste != null && !detalleYaExiste.getId().equals(detalleAReemplazar.getId())) {
                    detalleYaExiste.setCantidad(detalleYaExiste.getCantidad() + cantidadMencionada);
                    double pUnit = prodMencionado.getPrecio() != null ? prodMencionado.getPrecio() : 0.0;
                    detalleYaExiste.setPrecioUnitario(pUnit);
                    pedidoDetalleRepository.save(detalleYaExiste);
                    pedidoDetalleRepository.delete(detalleAReemplazar);
                } else {
                    detalleAReemplazar.setProducto(prodMencionado);
                    detalleAReemplazar.setCantidad(cantidadMencionada);
                    double pUnit = prodMencionado.getPrecio() != null ? prodMencionado.getPrecio() : 0.0;
                    detalleAReemplazar.setPrecioUnitario(pUnit);
                    pedidoDetalleRepository.save(detalleAReemplazar);
                }
            } else if (detalleYaExiste != null) {
                detalleYaExiste.setCantidad(cantidadMencionada);
                double pUnit = prodMencionado.getPrecio() != null ? prodMencionado.getPrecio() : 0.0;
                detalleYaExiste.setPrecioUnitario(pUnit);
                pedidoDetalleRepository.save(detalleYaExiste);
            } else {
                PedidoDetalle nuevoDet = new PedidoDetalle();
                nuevoDet.setPedido(pedidoPendienteActivo);
                nuevoDet.setProducto(prodMencionado);
                nuevoDet.setCantidad(cantidadMencionada);
                double pUnit = prodMencionado.getPrecio() != null ? prodMencionado.getPrecio() : 0.0;
                nuevoDet.setPrecioUnitario(pUnit);
                pedidoDetalleRepository.save(nuevoDet);
            }

            // LIMPIAR OBLIGATORIAMENTE CUALQUIER ÍTEM AGOTADO REMANENTE PARA NO ACUMULAR O DUPLICAR TOTALES
            List<PedidoDetalle> verificarDetalles = pedidoDetalleRepository.findByPedidoId(pedidoPendienteActivo.getId());
            if (verificarDetalles != null) {
                for (PedidoDetalle d : verificarDetalles) {
                    if (d.getProducto() != null) {
                        int st = d.getProducto().getStock() != null ? d.getProducto().getStock() : 0;
                        if (st <= 0) {
                            pedidoDetalleRepository.delete(d);
                        }
                    }
                }
            }

            // Recalcular el total y los detalles del pedido únicamente con los ítems activos
            List<PedidoDetalle> detallesActualizados = pedidoDetalleRepository.findByPedidoId(pedidoPendienteActivo.getId());
            double sumaTotales = 0.0;
            StringBuilder sbVars = new StringBuilder();
            StringBuilder sbDesglose = new StringBuilder();

            for (PedidoDetalle d : detallesActualizados) {
                double p = d.getPrecioUnitario() != null ? d.getPrecioUnitario() : (d.getProducto() != null && d.getProducto().getPrecio() != null ? d.getProducto().getPrecio() : 0.0);
                int c = d.getCantidad() != null ? d.getCantidad() : 1;
                double sub = p * c;
                sumaTotales += sub;
                String nom = d.getProducto() != null ? d.getProducto().getNombre() : "Producto";

                if (sbVars.length() > 0) sbVars.append(", ");
                sbVars.append(nom).append(" x").append(c);

                sbDesglose.append("- ").append(nom).append(" x").append(c)
                          .append(" (S/ ").append(String.format("%.2f", sub)).append(")\n");
            }

            pedidoPendienteActivo.setTotal(sumaTotales);
            pedidoPendienteActivo.setVariantesEspecificas(sbVars.toString());
            pedidoRepository.save(pedidoPendienteActivo);
            log.info("[OpenAI Service] Reemplazo de stock exitoso en Pedido ID {}. Nuevo total: S/ {}, Variantes: {}",
                    pedidoPendienteActivo.getId(), sumaTotales, sbVars.toString());

            res.respuestaSugerida = "¡Perfecto! Hemos actualizado tu pedido:\n" +
                    sbDesglose.toString() +
                    "Total a pagar: S/ " + String.format("%.2f", sumaTotales) + "\n\n" +
                    "¿Estás conforme con este detalle para continuar con tus datos de entrega? 😊";
            res.nuevoContextoInmediato = "CONFIRMACION_REEMPLAZO";
            res.prioridad = "INTERMEDIA";
            return res;
        }

        // Si no pudo resolver producto ni cantidad, preguntar cordialmente qué producto desea
        res.respuestaSugerida = "¡Hola! Por favor indícanos por cuál producto de nuestro catálogo te gustaría reemplazarlo y cuántas unidades deseas 😊.";
        res.nuevoContextoInmediato = "STOCK_INSUFICIENTE";
        return res;
    }
}
