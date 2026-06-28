package com.upc.DTO.Whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Representa cada elemento del array "entry" en el payload de Meta.
 *
 * {
 *   "id": "WHATSAPP_BUSINESS_ACCOUNT_ID",
 *   "changes": [ { ... } ]
 * }
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WhatsappEntryDTO {

    @JsonProperty("id")
    private String id;

    @JsonProperty("changes")
    private List<WhatsappChangeDTO> changes;
}
