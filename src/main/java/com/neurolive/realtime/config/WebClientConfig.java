package com.neurolive.realtime.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

// Configura el cliente HTTP interno hacia el backend principal.
@Configuration
public class WebClientConfig {

    // Construye el WebClient base para integraciones internas.
    @Bean
    public WebClient backendWebClient(RealtimeProperties props) {
        String baseUrl = props.getIntegration().getBaseUrl();
        String token = props.getIntegration().getInternalToken();
        return WebClient.builder()
                .baseUrl(baseUrl == null || baseUrl.isBlank()
                        ? "https://neurolive-backend.azurewebsites.net"
                        : baseUrl)
                .defaultHeader("X-Internal-Token", token != null ? token : "")
                .build();
    }
}

