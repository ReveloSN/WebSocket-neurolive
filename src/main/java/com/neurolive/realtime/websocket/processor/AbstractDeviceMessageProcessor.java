package com.neurolive.realtime.websocket.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neurolive.realtime.exception.DeviceAuthenticationException;
import com.neurolive.realtime.exception.InvalidDeviceMessageException;
import com.neurolive.realtime.websocket.DeviceSessionAttributes;
import org.springframework.web.socket.WebSocketSession;

// Ofrece utilidades comunes para procesar mensajes.
public abstract class AbstractDeviceMessageProcessor {

    protected final ObjectMapper objectMapper;

    // Guarda el serializador necesario para mapear JSON.
    protected AbstractDeviceMessageProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Convierte el JSON entrante al DTO solicitado.
    protected <T> T readMessage(JsonNode payload, Class<T> targetType) {
        try {
            return objectMapper.treeToValue(payload, targetType);
        } catch (JsonProcessingException exception) {
            throw new InvalidDeviceMessageException("Invalid payload for " + targetType.getSimpleName());
        }
    }

    // Verifica autenticacion y consistencia de deviceId.
    protected String requireAuthenticatedDevice(WebSocketSession session, String messageDeviceId) {
        String authenticatedDeviceId = DeviceSessionAttributes.getAuthenticatedDeviceId(session);
        if (authenticatedDeviceId == null || authenticatedDeviceId.isBlank()) {
            throw new DeviceAuthenticationException("Device must authenticate before sending operational messages");
        }
        if (messageDeviceId == null || messageDeviceId.isBlank()) {
            throw new InvalidDeviceMessageException("Message deviceId is required");
        }
        if (!authenticatedDeviceId.equals(messageDeviceId)) {
            throw new InvalidDeviceMessageException("Message deviceId does not match authenticated session");
        }
        return authenticatedDeviceId;
    }
}

