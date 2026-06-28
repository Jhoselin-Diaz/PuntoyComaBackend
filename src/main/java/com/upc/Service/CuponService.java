package com.upc.Service;

import com.upc.Entity.Cupon;
import java.util.List;
import java.util.Optional;

public interface CuponService {
    List<Cupon> listarTodos();
    Cupon guardar(Cupon cupon);
    Cupon actualizar(Long id, Cupon cupon);
    void eliminar(Long id);
    Optional<Cupon> validarCupon(String codigo);
}
