package com.upc.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovimientoInventarioDTO {
    private Long id;
    private Integer cantidad;
    private String tipoMovimiento;
    private String proveedor;
    private LocalDateTime fecha;
    private String notas;
    private Long productoId;
    private String productoNombre;
}
