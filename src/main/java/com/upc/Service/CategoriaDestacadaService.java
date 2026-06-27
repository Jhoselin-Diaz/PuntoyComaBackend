package com.upc.Service;

import com.upc.DTO.CategoriaDestacadaDTO;
import java.util.List;

public interface CategoriaDestacadaService {
    CategoriaDestacadaDTO crearCategoria(CategoriaDestacadaDTO dto);
    List<CategoriaDestacadaDTO> obtenerTodas();
    CategoriaDestacadaDTO obtenerPorId(Long id);
    CategoriaDestacadaDTO actualizarCategoria(Long id, CategoriaDestacadaDTO dto);
    void eliminarCategoria(Long id);
    CategoriaDestacadaDTO actualizarVisibilidad(Long id, Boolean visible);
}
