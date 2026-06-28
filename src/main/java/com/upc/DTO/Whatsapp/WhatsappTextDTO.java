package com.upc.DTO.Whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Contenido de texto de un mensaje de WhatsApp.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WhatsappTextDTO {

    /** Cuerpo del mensaje enviado por el cliente. */
    @JsonProperty("body")
    private String body;
}
