package com.upc.Service;

import com.upc.DTO.Whatsapp.WhatsappApiResponseDTO;
import com.upc.DTO.Whatsapp.WhatsappPayloadDTO;

/**
 * Contrato del servicio de WhatsApp Cloud API.
 * Cubre tanto la recepción (webhook entrante) como el envío de mensajes.
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
     * usando la API de WhatsApp Cloud de Meta.
     *
     * @param destinatario Número de teléfono en formato E.164 sin '+' (ej. "51987654321").
     * @param texto        Contenido del mensaje a enviar.
     * @return DTO con la respuesta de Meta (incluye el wamid del mensaje enviado).
     */
    WhatsappApiResponseDTO enviarMensajeTexto(String destinatario, String texto);
}

