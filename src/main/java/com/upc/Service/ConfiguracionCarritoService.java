package com.upc.Service;

import com.upc.Entity.ConfiguracionCarrito;

public interface ConfiguracionCarritoService {
    ConfiguracionCarrito obtenerConfiguracion();
    ConfiguracionCarrito guardarConfiguracion(ConfiguracionCarrito config);
}
