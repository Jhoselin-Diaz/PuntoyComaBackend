package com.upc.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MensajeDTO {
    private Long id;
    private String contenido;
    private String remitente;
    private LocalDateTime fechaEnvio;
    private String wamid;
    private Long chatId;
}
