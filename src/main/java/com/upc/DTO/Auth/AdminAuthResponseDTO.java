package com.upc.DTO.Auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminAuthResponseDTO {
    private boolean success;
    private String mensaje;
    private Long id;
    private String nombreCompleto;
    private String correo;
    private String rol;
}
