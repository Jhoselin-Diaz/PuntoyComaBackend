package com.upc.Config;

import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    @Bean
    public ModelMapper modelMapper() {
        return new ModelMapper();
    }

    /**
     * Bean de RestTemplate para realizar peticiones HTTP externas
     * (ej. WhatsApp Cloud API). Se registra aquí para poder ser
     * inyectado vía constructor en cualquier servicio.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
