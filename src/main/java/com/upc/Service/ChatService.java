package com.upc.Service;

import com.upc.DTO.ChatDTO;
import com.upc.DTO.MensajeDTO;
import java.util.List;

public interface ChatService {
    List<ChatDTO> obtenerTodosLosChats();
    List<MensajeDTO> obtenerMensajesDeChat(Long chatId);
    MensajeDTO enviarMensajeAdmin(Long chatId, String texto);
    void registrarMensaje(String telefono, String lid, String nombreCliente, String contenido, String wamid, String remitente);
    void eliminarChat(Long chatId);
    void regenerarRespuestaIA(Long chatId);

    default void registrarMensaje(String telefono, String nombre, String contenido, String wamid, String remitente) {
        registrarMensaje(telefono, null, nombre, contenido, wamid, remitente);
    }

    default void registrarMensajeCliente(String telefono, String nombreCliente, String contenido, String wamid) {
        registrarMensaje(telefono, null, nombreCliente, contenido, wamid, "CLIENTE");
    }

    default void guardarMensajeEntrante(String telefono, String nombre, String texto, String wamid) {
        registrarMensaje(telefono, null, nombre, texto, wamid, "CLIENTE");
    }
}
