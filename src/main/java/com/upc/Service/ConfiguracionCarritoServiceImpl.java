package com.upc.Service;

import com.upc.Entity.ConfiguracionCarrito;
import com.upc.Repository.ConfiguracionCarritoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfiguracionCarritoServiceImpl implements ConfiguracionCarritoService {

    @Autowired
    private ConfiguracionCarritoRepository repository;

    /**
     * Lectura simple — no necesita transacción de escritura.
     * Si el registro aún no existe, delega la inicialización
     * a un método separado con su propia transacción.
     */
    @Override
    @Transactional(readOnly = true)
    public ConfiguracionCarrito obtenerConfiguracion() {
        return repository.findAll().stream().findFirst().orElseGet(this::inicializarConfiguracionPorDefecto);
    }

    /**
     * Inicialización del registro maestro — transacción de escritura
     * aislada; solo se ejecuta la primera vez que la tabla está vacía.
     */
    @Transactional
    public ConfiguracionCarrito inicializarConfiguracionPorDefecto() {
        return repository.findAll().stream().findFirst().orElseGet(() -> {
            ConfiguracionCarrito def = new ConfiguracionCarrito();
            def.setId(null); // Let the database auto-generate the ID
            def.setWhatsappUrl("https://wa.me/51933526011");
            def.setBeneficio1("Envíos a todo el Perú");
            def.setBeneficio2("Pago seguro coordinado por WhatsApp");
            def.setBeneficio3("Empaque cuidadoso para productos frágiles");
            def.setBeneficio4("Cambios y devoluciones dentro de 48 horas");
            return repository.save(def);
        });
    }

    @Override
    @Transactional
    public ConfiguracionCarrito guardarConfiguracion(ConfiguracionCarrito configRecibida) {
        ConfiguracionCarrito existente = repository.findAll().stream().findFirst().orElseGet(() -> {
            ConfiguracionCarrito c = new ConfiguracionCarrito();
            c.setId(null); // Let database auto-generate ID if empty
            return c;
        });
        existente.setWhatsappUrl(configRecibida.getWhatsappUrl());
        existente.setBeneficio1(configRecibida.getBeneficio1() != null ? configRecibida.getBeneficio1() : "");
        existente.setBeneficio2(configRecibida.getBeneficio2() != null ? configRecibida.getBeneficio2() : "");
        existente.setBeneficio3(configRecibida.getBeneficio3() != null ? configRecibida.getBeneficio3() : "");
        existente.setBeneficio4(configRecibida.getBeneficio4() != null ? configRecibida.getBeneficio4() : "");
        return repository.save(existente);
    }
}
