package com.upc.DTO.Whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa cada elemento del array "changes" dentro de un entry.
 *
 * {
 *   "value": { ... },
 *   "field": "messages"
 * }
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WhatsappChangeDTO {

    @JsonProperty("value")
    private WhatsappValueDTO value;

    @JsonProperty("field")
    private String field;
}
