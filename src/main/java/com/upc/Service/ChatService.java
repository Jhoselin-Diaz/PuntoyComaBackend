package com.upc.Service;

import com.upc.DTO.ChatDTO;
import com.upc.DTO.MensajeDTO;
import java.util.List;

public interface ChatService {
    List<ChatDTO> obtenerTodosLosChats();
    List<MensajeDTO> obtenerMensajesDeChat(Long chatId);
    MensajeDTO enviarMensajeAdmin(Long chatId, String texto);
    void registrarMensajeCliente(String telefono, String nombreCliente, String contenido, String wamid);

    default void guardarMensajeEntrante(String telefono, String nombre, String texto, String wamid) {
        registrarMensajeCliente(telefono, nombre, texto, wamid);
    }
}
