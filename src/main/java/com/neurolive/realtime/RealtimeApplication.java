package com.neurolive.realtime;

import com.neurolive.realtime.config.RealtimeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

// Inicia el servicio realtime de Neuro Live.
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(RealtimeProperties.class)
public class RealtimeApplication {

    // Arranca la aplicación Spring Boot.
    public static void main(String[] args) {
        SpringApplication.run(RealtimeApplication.class, args);
    }
}

