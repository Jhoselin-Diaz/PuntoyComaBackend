package com.upc.DTO.Auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminCambiarPasswordDTO {
    private String correo;
    private String passwordActual;
    private String passwordNueva;
}
