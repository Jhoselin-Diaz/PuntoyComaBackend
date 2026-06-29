package com.upc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class PuntoyComaBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(PuntoyComaBackendApplication.class, args);
    }

}
