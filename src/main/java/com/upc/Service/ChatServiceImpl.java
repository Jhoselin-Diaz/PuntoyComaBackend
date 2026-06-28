package com.upc.Service;

import com.upc.DTO.ChatDTO;
import com.upc.DTO.MensajeDTO;
import com.upc.Entity.Chat;
import com.upc.Entity.Mensaje;
import com.upc.Entity.Usuario;
import com.upc.Repository.ChatRepository;
import com.upc.Repository.MensajeRepository;
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

    @Override
    @Transactional(readOnly = true)
    public List<ChatDTO> obtenerTodosLosChats() {
        return chatRepository.findAllByOrderByFechaUltimaActualizacionDesc().stream()
                .map(this::convertChatToDto)
                .collect(Collectors.toList());
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
        chat.setUltimoMensaje(texto);
        chat.setFechaUltimaActualizacion(LocalDateTime.now());
        chat.setUnreadCount(0);
        chatRepository.save(chat);

        return convertMensajeToDto(guardado);
    }

    @Override
    @Transactional
    public void registrarMensajeCliente(String telefono, String nombreCliente, String contenido, String wamid) {
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

        // Actualizar datos del chat
        chat.setUltimoMensaje(contenido);
        chat.setFechaUltimaActualizacion(LocalDateTime.now());
        chat.setUnreadCount(chat.getUnreadCount() + 1);
        Chat chatGuardado = chatRepository.save(chat);

        // Guardar burbuja de mensaje
        Mensaje mensaje = new Mensaje();
        mensaje.setChat(chatGuardado);
        mensaje.setContenido(contenido);
        mensaje.setRemitente("CLIENTE");
        mensaje.setFechaEnvio(LocalDateTime.now());
        mensaje.setWamid(wamid);
        mensajeRepository.save(mensaje);
    }

    // ── Mapeos manuales ──────────────────────────────────────────────────────

    private ChatDTO convertChatToDto(Chat chat) {
        ChatDTO dto = new ChatDTO();
        dto.setId(chat.getId());
        dto.setTelefonoCliente(chat.getTelefonoCliente());
        dto.setNombreCliente(chat.getNombreCliente());
        dto.setUltimoMensaje(chat.getUltimoMensaje());
        dto.setFechaUltimaActualizacion(chat.getFechaUltimaActualizacion());
        dto.setUnreadCount(chat.getUnreadCount());
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
}
