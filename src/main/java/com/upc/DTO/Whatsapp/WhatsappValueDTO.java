package com.upc.DTO.Whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Representa el objeto "value" dentro de cada change.
 * Contiene los mensajes, contactos y metadatos del negocio.
 *
 * {
 *   "messaging_product": "whatsapp",
 *   "metadata": { "display_phone_number": "...", "phone_number_id": "..." },
 *   "contacts": [ { "profile": { "name": "..." }, "wa_id": "..." } ],
 *   "messages": [ { "from": "...", "id": "...", "timestamp": "...", "text": { "body": "..." }, "type": "text" } ]
 * }
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WhatsappValueDTO {

    @JsonProperty("messaging_product")
    private String messagingProduct;

    @JsonProperty("metadata")
    private WhatsappMetadataDTO metadata;

    @JsonProperty("contacts")
    private List<WhatsappContactDTO> contacts;

    @JsonProperty("messages")
    private List<WhatsappMessageDTO> messages;
}
