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
        if (cupon.getUsosActuales() == null) {
            cupon.setUsosActuales(0);
        }
        return repository.save(cupon);
    }

    @Override
    public Cupon actualizar(Long id, Cupon cuponDetails) {
        Cupon cupon = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cupón no encontrado con id: " + id));
        cupon.setCodigo(cuponDetails.getCodigo());
        cupon.setPorcentajeDescuento(cuponDetails.getPorcentajeDescuento());
        cupon.setActivo(cuponDetails.getActivo());
        cupon.setMontoMinimo(cuponDetails.getMontoMinimo());
        cupon.setFechaInicio(cuponDetails.getFechaInicio());
        cupon.setFechaFin(cuponDetails.getFechaFin());
        cupon.setLimiteUso(cuponDetails.getLimiteUso());
        if (cuponDetails.getUsosActuales() != null) {
            cupon.setUsosActuales(cuponDetails.getUsosActuales());
        }
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
        Optional<Cupon> cuponOpt = repository.findByCodigoIgnoreCaseAndActivoTrue(codigo.trim());
        if (cuponOpt.isEmpty()) {
            return Optional.empty();
        }
        Cupon cupon = cuponOpt.get();
        java.time.LocalDate hoy = java.time.LocalDate.now();
        // Validar vigencia de fechas
        if (cupon.getFechaInicio() != null && hoy.isBefore(cupon.getFechaInicio())) {
            return Optional.empty();
        }
        if (cupon.getFechaFin() != null && hoy.isAfter(cupon.getFechaFin())) {
            return Optional.empty();
        }
        // Validar límite de usos
        if (cupon.getLimiteUso() != null && cupon.getLimiteUso() > 0) {
            int usos = cupon.getUsosActuales() != null ? cupon.getUsosActuales() : 0;
            if (usos >= cupon.getLimiteUso()) {
                return Optional.empty();
            }
        }
        return Optional.of(cupon);
    }

    @Override
    public Cupon registrarUso(Long id) {
        Cupon cupon = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cupón no encontrado con id: " + id));
        int actuales = cupon.getUsosActuales() != null ? cupon.getUsosActuales() : 0;
        cupon.setUsosActuales(actuales + 1);
        return repository.save(cupon);
    }
}

