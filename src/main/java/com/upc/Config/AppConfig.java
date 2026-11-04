package com.upc.Config;

import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class AppConfig {

    @Bean
    public ModelMapper modelMapper() {
        return new ModelMapper();
    }

    /**
     * Bean de RestTemplate para realizar peticiones HTTP externas
     * (ej. WhatsApp Cloud API, OpenAI). Se configuran timeouts estrictos
     * para evitar que llamadas externas colgadas retengan hilos y conexiones a BD.
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(12));
        return new RestTemplate(factory);
    }

    @Bean
    public com.upc.Service.OcrService ocrService() {
        return new com.upc.Service.OcrServiceImpl();
    }
}
