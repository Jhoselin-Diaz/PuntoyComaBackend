package com.upc.Service;

import com.upc.Entity.ConfiguracionNegocio;

public interface ConfiguracionNegocioService {
    ConfiguracionNegocio obtenerConfiguracion();
    ConfiguracionNegocio guardarConfiguracion(ConfiguracionNegocio configuracion);
}
