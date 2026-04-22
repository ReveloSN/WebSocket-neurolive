package com.neurolive.realtime.service;

import com.neurolive.realtime.config.RealtimeProperties;
import com.neurolive.realtime.dto.AuthMessage;
import com.neurolive.realtime.exception.DeviceAuthenticationException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

// Valida la identidad basica del dispositivo mediante token.
@Service
public class DeviceAuthenticationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceAuthenticationService.class);

    private final RealtimeProperties realtimeProperties;
    private final WebClient backendWebClient;

    // Recibe la configuracion de seguridad y el cliente interno.
    public DeviceAuthenticationService(RealtimeProperties realtimeProperties,
                                       WebClient backendWebClient) {
        this.realtimeProperties = realtimeProperties;
        this.backendWebClient = backendWebClient;
    }

    // Valida el mensaje de autenticacion o lanza una excepcion.
    public void authenticateOrThrow(AuthMessage authMessage) {
        if (authMessage == null) {
            throw new DeviceAuthenticationException("Authentication payload is required");
        }
        if (isBlank(authMessage.deviceId()) || isBlank(authMessage.token())) {
            throw new DeviceAuthenticationException("deviceId and token are required");
        }

        Map<String, String> localTokens = realtimeProperties.getSecurity().getDeviceTokens();
        if (!localTokens.isEmpty()) {
            String expected = localTokens.get(authMessage.deviceId());
            if (expected != null && !expected.isBlank()) {
                if (!Objects.equals(expected, authMessage.token())) {
                    throw new DeviceAuthenticationException("Invalid device credentials");
                }
                return;
            }
        }

        boolean valid = validateTokenAgainstBackend(authMessage.deviceId(), authMessage.token());
        if (!valid) {
            throw new DeviceAuthenticationException("Invalid device credentials");
        }
    }

    // Retorna los dispositivos configurados en memoria.
    public Set<String> getConfiguredDeviceIds() {
        return Set.copyOf(realtimeProperties.getSecurity().getDeviceTokens().keySet());
    }

    // Consulta al backend si el token del dispositivo es valido.
    private boolean validateTokenAgainstBackend(String deviceId, String token) {
        try {
            Boolean result = backendWebClient.post()
                    .uri("/internal/devices/validate-token")
                    .bodyValue(Map.of("deviceId", deviceId, "token", token))
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block(Duration.ofMillis(realtimeProperties.getIntegration().getTimeoutMs()));
            return Boolean.TRUE.equals(result);
        } catch (Exception exception) {
            LOGGER.warn("Backend token validation failed for deviceId={}: {}", deviceId, exception.getMessage());
            return false;
        }
    }

    // Evalua si un texto esta vacio.
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
