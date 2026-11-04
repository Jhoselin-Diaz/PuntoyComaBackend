package com.upc.Service;

import com.upc.DTO.ChatDTO;
import com.upc.DTO.MensajeDTO;
import com.upc.Entity.Chat;
import com.upc.Entity.Mensaje;
import com.upc.Entity.Pedido;
import com.upc.Entity.Usuario;
import com.upc.Repository.ChatRepository;
import com.upc.Repository.MensajeRepository;
import com.upc.Repository.PedidoRepository;
import com.upc.Repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatServiceImpl implements ChatService {

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private MensajeRepository mensajeRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private WhatsappService whatsappService;

    @Autowired
    private OpenAiService openAiService;

    @Autowired
    private ConciliacionVoucherService conciliacionVoucherService;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ChatDTO> obtenerTodosLosChats() {
        try {
            List<Chat> chats = chatRepository.findAllChatsOrderedByPriorityAndDate();
            if (chats == null) {
                return new java.util.ArrayList<>();
            }
            return chats.stream()
                    .map(this::convertChatToDto)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("[ChatService] Error en obtenerTodosLosChats: " + e.getMessage());
            e.printStackTrace();
            return new java.util.ArrayList<>();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<MensajeDTO> obtenerMensajesDeChat(Long chatId) {
        if (!chatRepository.existsById(chatId)) {
            throw new RuntimeException("Chat no encontrado con ID: " + chatId);
        }
        return mensajeRepository.findByChatIdOrderByFechaEnvioAsc(chatId).stream()
                .map(this::convertMensajeToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MensajeDTO enviarMensajeAdmin(Long chatId, String texto) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat no encontrado con ID: " + chatId));

        // Enviar el mensaje a través de Meta WhatsApp Cloud API
        var apiResponse = whatsappService.enviarMensajeTexto(chat.getTelefonoCliente(), texto);
        String wamid = null;
        if (apiResponse != null && apiResponse.getMessages() != null && !apiResponse.getMessages().isEmpty()) {
            wamid = apiResponse.getMessages().get(0).getId();
        }

        // Crear y guardar el mensaje en la BD
        Mensaje mensaje = new Mensaje();
        mensaje.setChat(chat);
        mensaje.setContenido(texto);
        mensaje.setRemitente("ADMINISTRADOR");
        mensaje.setFechaEnvio(LocalDateTime.now());
        mensaje.setWamid(wamid);
        Mensaje guardado = mensajeRepository.save(mensaje);

        // Actualizar datos del chat
        chat.setUltimoMensaje(sanitizarUltimoMensaje(texto));
        chat.setFechaUltimaActualizacion(LocalDateTime.now());
        chat.setUnreadCount(0);
        chatRepository.save(chat);

        return convertMensajeToDto(guardado);
    }

    @Override
    @Transactional
    public void registrarMensaje(String telefono, String lid, String nombreCliente, String contenido, String wamid, String remitente) {
        // 0. Protección contra duplicados basada en el ID real de WhatsApp (wamid) con actualización de vouchers fallidos
        if (wamid != null && !wamid.trim().isEmpty()) {
            java.util.Optional<Mensaje> existenteOpt = mensajeRepository.findByWamid(wamid.trim());
            if (existenteOpt.isPresent()) {
                Mensaje msgExistente = existenteOpt.get();
                boolean esVoucher = (contenido != null && (contenido.startsWith("[VOUCHER]") || contenido.startsWith("data:image/")));
                boolean msgExistenteEsFallido = msgExistente.getContenido() != null && 
                    (msgExistente.getContenido().contains("[VOUCHER_FALLIDO]") || msgExistente.getContenido().equals("[Archivo]"));

                if (esVoucher && msgExistenteEsFallido) {
                    System.out.println("[WhatsApp Deduplicador] Actualizando mensaje previo fallido " + wamid + " con voucher descargado exitosamente.");
                    msgExistente.setContenido(contenido);
                    mensajeRepository.save(msgExistente);

                    Chat chatExistente = msgExistente.getChat();
                    Pedido pedidoActivo = pedidoRepository.findTopByUsuarioTelefonoAndEstadoOrderByFechaDesc(telefono, "PENDIENTE");
                    if (pedidoActivo == null) {
                        pedidoActivo = pedidoRepository.findTopByUsuarioTelefonoAndEstadoOrderByFechaDesc(telefono, "EN REVISION");
                    }

                    if (chatExistente != null && pedidoActivo != null) {
                        chatExistente.setPedidoReferenciadoId(pedidoActivo.getId());
                        chatExistente.setUltimoMensaje(sanitizarUltimoMensaje(contenido));
                        chatExistente.setFechaUltimaActualizacion(LocalDateTime.now());
                        chatExistente.setSugerenciaIa("¡Muchas gracias por tu comprobante! 😊 Hemos recibido tu pago y se encuentra en validación. En breve confirmaremos tu pedido y coordinaremos el despacho.");
                        chatExistente.setFasePedido("CONCILIACION");
                        chatExistente.setDatosCompletos(true);
                        chatExistente.setContextoInmediato(null);
                        chatRepository.save(chatExistente);
                    } else if (chatExistente != null) {
                        chatExistente.setUltimoMensaje(sanitizarUltimoMensaje(contenido));
                        chatExistente.setFechaUltimaActualizacion(LocalDateTime.now());
                        chatRepository.save(chatExistente);
                        System.out.println("[ChatService] [VOUCHER] recibido pero cliente " + telefono + " no tiene pedidos PENDIENTE ni EN REVISION. Se omite actualización de fase.");
                    }

                    if ("CLIENTE".equalsIgnoreCase(remitente) && pedidoActivo != null) {
                        conciliacionVoucherService.procesarYConciliarVoucher(telefono, contenido, wamid);
                    }
                    return;
                }

                System.out.println("[WhatsApp Deduplicador] Mensaje duplicado omitido por wamid existente: " + wamid);
                return;
            }
        }

        // 1. Intentar buscar por teléfono real
        Chat chat = chatRepository.findByTelefonoCliente(telefono).orElse(null);

        // 2. Si no lo encuentra por teléfono, pero nos pasaron un LID, buscar por LID
        if (chat == null && lid != null && !lid.trim().isEmpty() && !lid.equals(telefono)) {
            chat = chatRepository.findByTelefonoCliente(lid).orElse(null);
            if (chat != null) {
                // ¡MIGRACIÓN AUTOMÁTICA! Encontramos el chat guardado con el LID antiguo.
                // Lo actualizamos al teléfono real para unificarlo e impedir duplicados futuros.
                System.out.println("[WhatsApp] Migrando chat antiguo de LID " + lid + " a número real " + telefono);
                chat.setTelefonoCliente(telefono);
                chat = chatRepository.save(chat);
            }
        }

        // 3. Si sigue siendo nulo, crear uno nuevo
        if (chat == null) {
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
            chat = nuevoChat;
        }

        // Actualizar datos del chat
        chat.setUltimoMensaje(sanitizarUltimoMensaje(contenido));
        chat.setFechaUltimaActualizacion(LocalDateTime.now());
        
        // Si el remitente es el administrador, ponemos a 0 los no leídos; si es cliente, sumamos 1
        if ("ADMINISTRADOR".equalsIgnoreCase(remitente)) {
            chat.setUnreadCount(0);
        } else {
            chat.setUnreadCount(chat.getUnreadCount() + 1);
        }

        // AUTO-DETECCIÓN PROACTIVA PARA CARRITO INICIAL:
        if ("CLIENTE".equalsIgnoreCase(remitente) && contenido != null) {
            String mLower = contenido.toLowerCase();
            boolean esCarritoInicialRapido = mLower.contains("confirmar mi pedido") || 
                    (mLower.contains("subtotal:") && mLower.contains("total:"));
            if (esCarritoInicialRapido) {
                chat.setPedidoIdentificado(true);
                chat.setFasePedido("INICIAL");
                chat.setPrioridad("NORMAL");
                if (chat.getSugerenciaIa() == null || chat.getSugerenciaIa().trim().isEmpty() || 
                    chat.getSugerenciaIa().contains("En espera de un nuevo mensaje") ||
                    chat.getSugerenciaIa().contains("comprobante") || chat.getSugerenciaIa().contains("pago")) {
                    String nombreSaludo = (chat.getNombreCliente() != null && !chat.getNombreCliente().trim().isEmpty() && !"Cliente Nuevo".equalsIgnoreCase(chat.getNombreCliente()) && !"Cliente".equalsIgnoreCase(chat.getNombreCliente()))
                            ? " " + chat.getNombreCliente().trim() : "";
                    chat.setSugerenciaIa("¡Hola" + nombreSaludo + "! Contamos con disponibilidad para tu pedido 😊. Para proceder con el despacho y confirmación, por favor compártenos tus datos de entrega:\n" + OpenAiService.PLANTILLA_DATOS_ENTREGA);
                }
                chat = chatRepository.saveAndFlush(chat);
            }
        }
        
        Chat chatGuardado = chatRepository.save(chat);

        // Guardar o actualizar burbuja de mensaje
        Mensaje mensaje = null;
        if (wamid != null && !wamid.trim().isEmpty()) {
            java.util.Optional<Mensaje> existente = mensajeRepository.findByWamid(wamid);
            if (existente.isPresent()) {
                mensaje = existente.get();
            }
        }
        if (mensaje == null) {
            mensaje = new Mensaje();
            mensaje.setChat(chatGuardado);
            mensaje.setRemitente(remitente);
            mensaje.setFechaEnvio(LocalDateTime.now());
            mensaje.setWamid(wamid);
        }
        mensaje.setContenido(contenido);
        mensajeRepository.save(mensaje);

        // Lanzar análisis asíncrono de OpenAI si el mensaje proviene del cliente y NO es un voucher/imagen
        if ("CLIENTE".equalsIgnoreCase(remitente)) {
            boolean esVoucher = (contenido != null && (contenido.startsWith("[VOUCHER]") || contenido.startsWith("data:image/")));
            boolean esVoucherFallido = (contenido != null && contenido.contains("[VOUCHER_FALLIDO]"));

            if (esVoucherFallido) {
                // Manejo inteligente: No enviar a OpenAI como texto conversacional
                chatGuardado.setSugerenciaIa("⚠️ El cliente intentó enviar un comprobante de pago pero ocurrió un error en la descarga de WhatsApp. Solicitar reenvío manual del comprobante.");
                chatGuardado.setPrioridad("ALTA");
                chatGuardado.setFasePedido("CONCILIACION");
                chatRepository.save(chatGuardado);
                System.out.println("[ChatService] [VOUCHER_FALLIDO] detectado para cliente " + telefono + ". Se actualizó el chat con aviso para el administrador.");
            } else if (!esVoucher) {
                try {
                    if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
                        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                            new org.springframework.transaction.support.TransactionSynchronization() {
                                @Override
                                public void afterCommit() {
                                    openAiService.procesarMensajeConIA(chatGuardado.getId(), telefono, contenido);
                                }
                            }
                        );
                    } else {
                        openAiService.procesarMensajeConIA(chatGuardado.getId(), telefono, contenido);
                    }
                } catch (Exception e) {
                    System.err.println("[ChatService] Error lanzando procesamiento de OpenAI de forma asíncrona: " + e.getMessage());
                }
            } else {
                // Mensaje con comprobante detectado: solo vincular si existe un pedido PENDIENTE o EN REVISION activo
                Pedido pedidoActivo = pedidoRepository.findTopByUsuarioTelefonoAndEstadoOrderByFechaDesc(telefono, "PENDIENTE");
                if (pedidoActivo == null) {
                    pedidoActivo = pedidoRepository.findTopByUsuarioTelefonoAndEstadoOrderByFechaDesc(telefono, "EN REVISION");
                }

                if (pedidoActivo != null) {
                    chatGuardado.setPedidoReferenciadoId(pedidoActivo.getId());
                    chatGuardado.setSugerenciaIa("¡Muchas gracias por tu comprobante! 😊 Hemos recibido tu pago y se encuentra en validación. En breve confirmaremos tu pedido y coordinaremos el despacho.");
                    chatGuardado.setFasePedido("CONCILIACION");
                    chatGuardado.setDatosCompletos(true);
                    chatGuardado.setContextoInmediato(null);
                    chatRepository.save(chatGuardado);
                    System.out.println("[ChatService] [VOUCHER] asociado exitosamente al pedido ID " + pedidoActivo.getId() + " para cliente " + telefono + ".");

                    try {
                        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
                            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                                new org.springframework.transaction.support.TransactionSynchronization() {
                                    @Override
                                    public void afterCommit() {
                                        conciliacionVoucherService.procesarYConciliarVoucher(telefono, contenido, wamid);
                                    }
                                }
                            );
                        } else {
                            conciliacionVoucherService.procesarYConciliarVoucher(telefono, contenido, wamid);
                        }
                    } catch (Exception e) {
                        System.err.println("[ChatService] Error disparando conciliación OCR: " + e.getMessage());
                    }
                } else {
                    System.out.println("[ChatService] [VOUCHER] detectado pero cliente " + telefono + " no tiene pedidos PENDIENTE ni EN REVISION. Se ignora voucher histórico.");
                }
            }
        }
    }

    @Override
    @Transactional
    public void eliminarChat(Long chatId) {
        List<Mensaje> mensajes = mensajeRepository.findByChatIdOrderByFechaEnvioAsc(chatId);
        if (mensajes != null && !mensajes.isEmpty()) {
            mensajeRepository.deleteAll(mensajes);
        }
        chatRepository.deleteById(chatId);
    }

    private String sanitizarUltimoMensaje(String msg) {
        if (msg == null) return null;
        if (msg.contains("[VOUCHER]") || msg.contains("data:image/")) {
            return "📷 Foto";
        }
        if (msg.length() > 180) {
            return msg.substring(0, 120) + "...";
        }
        return msg;
    }

    private ChatDTO convertChatToDto(Chat chat) {
        if (chat == null) return null;
        ChatDTO dto = new ChatDTO();
        dto.setId(chat.getId());
        dto.setTelefonoCliente(chat.getTelefonoCliente());
        dto.setNombreCliente(chat.getNombreCliente());
        dto.setUltimoMensaje(sanitizarUltimoMensaje(chat.getUltimoMensaje()));
        dto.setFechaUltimaActualizacion(chat.getFechaUltimaActualizacion());
        dto.setUnreadCount(chat.getUnreadCount() != null ? chat.getUnreadCount() : 0);
        dto.setPrioridad(chat.getPrioridad());
        dto.setSugerenciaIa(chat.getSugerenciaIa());
        dto.setMensajeGenerado(chat.getSugerenciaIa());
        dto.setPedidoReferenciadoId(chat.getPedidoReferenciadoId());
        dto.setPedidoIdentificado(chat.getPedidoIdentificado());
        dto.setDireccionDetectada(chat.getDireccionDetectada());
        dto.setDatosCompletos(chat.getDatosCompletos());
        dto.setFasePedido(chat.getFasePedido());
        dto.setContextoInmediato(chat.getContextoInmediato());
        dto.setTotalPedidos(0);
        
        return dto;
    }

    private MensajeDTO convertMensajeToDto(Mensaje mensaje) {
        MensajeDTO dto = new MensajeDTO();
        dto.setId(mensaje.getId());
        dto.setContenido(mensaje.getContenido());
        dto.setRemitente(mensaje.getRemitente());
        dto.setFechaEnvio(mensaje.getFechaEnvio());
        dto.setWamid(mensaje.getWamid());
        if (mensaje.getChat() != null) {
            dto.setChatId(mensaje.getChat().getId());
        }
        return dto;
    }

    @Override
    public com.upc.DTO.ChatDTO regenerarRespuestaIA(Long chatId) {
        return regenerarRespuestaIA(chatId, null);
    }

    @Override
    public com.upc.DTO.ChatDTO regenerarRespuestaIA(Long chatId, String sugerenciaActual) {
        Chat chat = chatRepository.findById(chatId).orElseThrow(() -> new RuntimeException("Chat no encontrado"));
        String prevSugerencia = (sugerenciaActual != null && !sugerenciaActual.trim().isEmpty())
                ? sugerenciaActual.trim()
                : (chat.getSugerenciaIa() != null ? chat.getSugerenciaIa().trim() : "");

        List<Mensaje> msgs = mensajeRepository.findTop10ByChatIdOrderByFechaEnvioDesc(chatId);
        String ultimoContenido = "";
        for (Mensaje m : msgs) {
            if ("CLIENTE".equalsIgnoreCase(m.getRemitente())) {
                ultimoContenido = m.getContenido();
                break;
            }
        }

        String ultimoContenidoLower = ultimoContenido != null ? ultimoContenido.toLowerCase() : "";
        boolean esCarrito = ultimoContenidoLower.contains("confirmar mi pedido") || 
                (ultimoContenidoLower.contains("subtotal:") && ultimoContenidoLower.contains("total:"));
        boolean tienePedidoValido = chat.getPedidoReferenciadoId() != null && pedidoRepository.existsById(chat.getPedidoReferenciadoId());

        if (esCarrito && !tienePedidoValido) {
            System.out.println("[ChatService] regenerarRespuestaIA detectó carrito sin Pedido en BD. Creando pedido y sincronizando de forma síncrona...");
            openAiService.procesarMensajeConIASincrono(chatId, chat.getTelefonoCliente(), ultimoContenido, false, prevSugerencia);
        } else {
            openAiService.generarSoloSugerenciaIA(chatId, chat.getTelefonoCliente(), ultimoContenido, prevSugerencia);
        }

        Chat chatActualizado = chatRepository.findById(chatId).orElse(chat);
        if (prevSugerencia != null && !prevSugerencia.isEmpty() && prevSugerencia.equalsIgnoreCase(chatActualizado.getSugerenciaIa())) {
            String vars = "tus productos seleccionados";
            if (chatActualizado.getPedidoReferenciadoId() != null) {
                Pedido p = pedidoRepository.findById(chatActualizado.getPedidoReferenciadoId()).orElse(null);
                if (p != null && p.getVariantesEspecificas() != null && !p.getVariantesEspecificas().trim().isEmpty()) {
                    vars = p.getVariantesEspecificas().trim();
                }
            }
            String alternativa;
            if ("CONCILIACION".equalsIgnoreCase(chatActualizado.getFasePedido()) || prevSugerencia.contains("comprobante") || prevSugerencia.contains("pago")) {
                alternativa = openAiService.obtenerAlternativaVoucher(prevSugerencia, null);
            } else {
                alternativa = openAiService.obtenerAlternativaMensajeInicial(vars, prevSugerencia);
            }
            chatActualizado.setSugerenciaIa(alternativa);
            chatRepository.save(chatActualizado);
        }
        return convertChatToDto(chatActualizado);
    }
}
