package com.upc.DTO.Whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadatos del número de teléfono del negocio que recibe el mensaje.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WhatsappMetadataDTO {

    /** Número de teléfono visible (formato legible). */
    @JsonProperty("display_phone_number")
    private String displayPhoneNumber;

    /** ID interno del número en Meta. */
    @JsonProperty("phone_number_id")
    private String phoneNumberId;
}
