package com.upc.DTO.Whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa cada contacto del array "contacts".
 * Contiene el nombre del perfil y el wa_id (número en formato E.164).
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WhatsappContactDTO {

    @JsonProperty("profile")
    private WhatsappProfileDTO profile;

    /** Número de teléfono del cliente en formato E.164 (ej. "51987654321"). */
    @JsonProperty("wa_id")
    private String waId;
}
