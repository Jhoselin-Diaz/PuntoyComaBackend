package com.upc.Service;

import com.upc.DTO.ContactoBloqueDTO;
import com.upc.DTO.ContactoCierreDTO;
import java.util.List;

public interface ContactoService {
    List<ContactoBloqueDTO> obtenerBloques();
    ContactoBloqueDTO actualizarBloque(String id, ContactoBloqueDTO bloqueDTO);
    ContactoCierreDTO obtenerCierre();
    ContactoCierreDTO actualizarCierre(ContactoCierreDTO cierreDTO);
}
