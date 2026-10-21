package com.upc.Service;

import com.upc.DTO.WhatsappStatusDTO;
import com.upc.DTO.Whatsapp.WhatsappApiResponseDTO;
import com.upc.DTO.Whatsapp.WhatsappPayloadDTO;

/**
 * Contrato del servicio de WhatsApp.
 * Cubre recepción, envío y consulta de estado del puente Node.js / WhatsApp Web.
 */
public interface WhatsappService {

    /**
     * Procesa el payload ya deserializado que llega del webhook de Meta.
     * Extrae el número de teléfono y el mensaje de texto de cada entrada.
     *
     * @param payload DTO raíz con la estructura completa del evento de Meta.
     */
    void procesarPayload(WhatsappPayloadDTO payload);

    /**
     * Envía un mensaje de texto plano a un número de WhatsApp
     * usando la API de WhatsApp Web / Evolution API / Meta.
     *
     * @param destinatario Número de teléfono en formato E.164 sin '+' (ej. "51987654321").
     * @param texto        Contenido del mensaje a enviar.
     * @return DTO con la respuesta del envío.
     */
    WhatsappApiResponseDTO enviarMensajeTexto(String destinatario, String texto);

    /**
     * Consulta el estado de conexión actual de WhatsApp Web y obtiene el código QR en Base64 si está disponible.
     *
     * @return WhatsappStatusDTO con el estado ("CONNECTED", "QR_READY", "CONNECTING", "DISCONNECTED") y el QR en Base64.
     */
    WhatsappStatusDTO obtenerEstadoWhatsapp();
}

