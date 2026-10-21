package com.upc.Service;

import com.upc.Entity.ConfiguracionNegocio;

import org.springframework.web.multipart.MultipartFile;

public interface ConfiguracionNegocioService {
    ConfiguracionNegocio obtenerConfiguracion();
    ConfiguracionNegocio guardarConfiguracion(ConfiguracionNegocio configuracion);
    ConfiguracionNegocio subirLogo(MultipartFile file);
}

