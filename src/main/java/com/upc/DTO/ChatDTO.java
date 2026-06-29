package com.upc.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatDTO {
    private Long id;
    private String telefonoCliente;
    private String nombreCliente;
    private String ultimoMensaje;
    private LocalDateTime fechaUltimaActualizacion;
    private Integer unreadCount;
    private String prioridad;
    private String sugerenciaIa;
    private Long pedidoReferenciadoId;
    private Boolean pedidoIdentificado;
    private Boolean direccionDetectada;
    private Boolean datosCompletos;
    private String fasePedido;
}
