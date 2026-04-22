package com.neurolive.realtime.service;

import com.neurolive.realtime.config.RealtimeProperties;
import com.neurolive.realtime.dto.AuthMessage;
import com.neurolive.realtime.exception.DeviceAuthenticationException;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

// Valida la identidad básica del dispositivo mediante token.
@Service
public class DeviceAuthenticationService {

    private final RealtimeProperties realtimeProperties;

    // Recibe la configuración de seguridad del servicio.
    public DeviceAuthenticationService(RealtimeProperties realtimeProperties) {
        this.realtimeProperties = realtimeProperties;
    }

    // Valida el mensaje de autenticación o lanza una excepción.
    public void authenticateOrThrow(AuthMessage authMessage) {
        if (authMessage == null) {
            throw new DeviceAuthenticationException("Authentication payload is required");
        }
        if (isBlank(authMessage.deviceId()) || isBlank(authMessage.token())) {
            throw new DeviceAuthenticationException("deviceId and token are required");
        }
        String expectedToken = realtimeProperties.getSecurity().getDeviceTokens().get(authMessage.deviceId());
        if (!Objects.equals(expectedToken, authMessage.token())) {
            throw new DeviceAuthenticationException("Invalid device credentials");
        }
    }

    // Retorna los dispositivos configurados en memoria.
    public Set<String> getConfiguredDeviceIds() {
        return Set.copyOf(realtimeProperties.getSecurity().getDeviceTokens().keySet());
    }

    // Evalúa si un texto está vacío.
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

