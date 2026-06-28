package com.upc.DTO.Whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa la respuesta que devuelve la API de WhatsApp Cloud
 * cuando se envía un mensaje correctamente.
 *
 * Ejemplo de respuesta exitosa de Meta:
 * {
 *   "messaging_product": "whatsapp",
 *   "contacts": [{ "input": "51987654321", "wa_id": "51987654321" }],
 *   "messages": [{ "id": "wamid.XXXXXXXXXX" }]
 * }
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WhatsappApiResponseDTO {

    @JsonProperty("messaging_product")
    private String messagingProduct;

    /** El wamid del mensaje enviado, útil para hacer seguimiento. */
    @JsonProperty("messages")
    private java.util.List<MensajeEnviadoDTO> messages;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MensajeEnviadoDTO {

        /** ID único del mensaje enviado (wamid.XXX). */
        @JsonProperty("id")
        private String id;

        /** Estado del mensaje (ej. "accepted"). */
        @JsonProperty("message_status")
        private String messageStatus;
    }
}
