package com.upc.Service;

import com.upc.DTO.ShopVideoDTO;
import java.util.List;

public interface ShopVideoService {
    ShopVideoDTO crearVideo(ShopVideoDTO shopVideoDTO);
    List<ShopVideoDTO> obtenerTodos();
    List<ShopVideoDTO> obtenerPublicos();
    ShopVideoDTO obtenerPorId(Long id);
    void eliminarVideo(Long id);
    ShopVideoDTO actualizarVisibilidad(Long id, Boolean visible);
    ShopVideoDTO actualizarVideo(Long id, ShopVideoDTO shopVideoDTO);
    void incrementarClicks(Long id);
}
