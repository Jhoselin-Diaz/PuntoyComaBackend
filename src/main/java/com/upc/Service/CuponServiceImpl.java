package com.upc.Service;

import com.upc.Entity.Cupon;
import com.upc.Repository.CuponRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class CuponServiceImpl implements CuponService {

    @Autowired
    private CuponRepository repository;

    @Override
    public List<Cupon> listarTodos() {
        return repository.findAll();
    }

    @Override
    public Cupon guardar(Cupon cupon) {
        return repository.save(cupon);
    }

    @Override
    public Cupon actualizar(Long id, Cupon cuponDetails) {
        Cupon cupon = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cupón no encontrado con id: " + id));
        cupon.setCodigo(cuponDetails.getCodigo());
        cupon.setPorcentajeDescuento(cuponDetails.getPorcentajeDescuento());
        cupon.setActivo(cuponDetails.getActivo());
        return repository.save(cupon);
    }

    @Override
    public void eliminar(Long id) {
        repository.deleteById(id);
    }

    @Override
    public Optional<Cupon> validarCupon(String codigo) {
        if (codigo == null || codigo.trim().isEmpty()) {
            return Optional.empty();
        }
        return repository.findByCodigoIgnoreCaseAndActivoTrue(codigo.trim());
    }
}
