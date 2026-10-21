package com.upc.DTO.Auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminRegistroDTO {
    private String nombreCompleto;
    private String correo;
    private String password;
}
