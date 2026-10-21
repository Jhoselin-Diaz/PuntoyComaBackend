package com.upc.Service;

import com.upc.DTO.DashboardVigilanciaDTO;
import com.upc.DTO.ProductoVigilanciaDTO;
import java.util.List;

public interface DashboardService {
    List<ProductoVigilanciaDTO> obtenerMasVendidos();
    List<ProductoVigilanciaDTO> obtenerAgotandose();
    List<ProductoVigilanciaDTO> obtenerSinExistencias();
    DashboardVigilanciaDTO obtenerVigilanciaStock();
}
