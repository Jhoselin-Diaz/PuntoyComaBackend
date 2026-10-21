package com.upc.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardVigilanciaDTO {
    private List<ProductoVigilanciaDTO> masVendidos;
    private List<ProductoVigilanciaDTO> agotandose;
    private List<ProductoVigilanciaDTO> sinExistencias;
    private List<ProductoVigilanciaDTO> destacados;
}
