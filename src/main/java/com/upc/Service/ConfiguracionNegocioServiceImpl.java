package com.upc.Service;

import com.upc.Entity.ConfiguracionNegocio;
import com.upc.Repository.ConfiguracionNegocioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ConfiguracionNegocioServiceImpl implements ConfiguracionNegocioService {

    @Autowired
    private ConfiguracionNegocioRepository repository;

    @Override
    public ConfiguracionNegocio obtenerConfiguracion() {
        return repository.findById(1L).orElseGet(() -> {
            ConfiguracionNegocio def = new ConfiguracionNegocio();
            def.setNombreTienda("Punto y Coma");
            def.setCorreo("contacto@puntoycoma.com");
            def.setTelefono("+51 999 999 999");
            def.setWhatsapp("+51 999 999 999");
            def.setInstagram("puntoycoma");
            def.setDireccion("Lima, Perú");
            def.setColorPrincipal("#ef6737");
            def.setSugerirAcciones(true);
            def.setValidarPagos(true);
            def.setDetectarIncompletos(true);
            def.setClasificarConversaciones(true);
            def.setBannerTitle("Ofertas Especiales");
            def.setBannerSubtitle("Aprovecha estas promociones exclusivas...");
            def.setBannerImage("https://images.unsplash.com/photo-1577918349257-2e1d7fbebf96");
            def.setBannerVisible(true);
            
            def.setOfertasBannerTitulo("Ofertas Especiales");
            def.setOfertasBannerSubtitulo("Aprovecha estas promociones exclusivas...");
            def.setOfertasBannerImg("https://images.unsplash.com/photo-1577918349257-2e1d7fbebf96");
            
            return repository.save(def);
        });
    }

    @Override
    public ConfiguracionNegocio guardarConfiguracion(ConfiguracionNegocio configuracion) {
        configuracion.setId(1L);
        return repository.save(configuracion);
    }
}
