package com.upc.DTO.Whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Representa el cuerpo raíz del JSON que envía Meta al webhook.
 *
 * Ejemplo de estructura:
 * {
 *   "object": "whatsapp_business_account",
 *   "entry": [ { ... } ]
 * }
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WhatsappPayloadDTO {

    @JsonProperty("object")
    private String object;

    @JsonProperty("entry")
    private List<WhatsappEntryDTO> entry;
}
