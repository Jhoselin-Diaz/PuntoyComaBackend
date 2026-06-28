package com.upc.DTO.Whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa un mensaje individual dentro del array "messages".
 *
 * Campos más usados para mensajes de texto:
 * {
 *   "from": "51987654321",         ← número del cliente (E.164, sin '+')
 *   "id": "wamid.XXXXXXX",         ← ID único del mensaje en Meta
 *   "timestamp": "1719500000",     ← epoch Unix
 *   "type": "text",
 *   "text": { "body": "Hola!" }
 * }
 *
 * Si el tipo es "image", "audio", etc., el campo "text" llega null
 * y Jackson lo ignora gracias a @JsonIgnoreProperties.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WhatsappMessageDTO {

    /** Número de teléfono del remitente en formato E.164 (sin '+'). */
    @JsonProperty("from")
    private String from;

    /** ID único del mensaje asignado por Meta (wamid.XXX). */
    @JsonProperty("id")
    private String id;

    /** Epoch Unix como String (segundos). */
    @JsonProperty("timestamp")
    private String timestamp;

    /**
     * Tipo de mensaje: "text", "image", "audio", "video",
     * "document", "sticker", "location", "contacts", etc.
     */
    @JsonProperty("type")
    private String type;

    /** Contenido de texto. Null si el tipo no es "text". */
    @JsonProperty("text")
    private WhatsappTextDTO text;
}
