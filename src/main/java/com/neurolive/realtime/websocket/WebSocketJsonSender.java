package com.neurolive.realtime.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neurolive.realtime.dto.ErrorMessage;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

// Envia mensajes JSON de forma segura por WebSocket.
@Component
public class WebSocketJsonSender {

    private final ObjectMapper objectMapper;

    // Recibe el serializador JSON compartido.
    public WebSocketJsonSender(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Serializa y envia un objeto a la sesion.
    public void sendMessage(WebSocketSession session, Object payload) throws IOException {
        if (!session.isOpen()) {
            throw new IOException("WebSocket session is closed");
        }
        String jsonPayload = writeJson(payload);
        synchronized (session) {
            session.sendMessage(new TextMessage(jsonPayload));
        }
    }

    // Envia un mensaje de error estandarizado.
    public void sendError(WebSocketSession session, String error, String message, String deviceId) throws IOException {
        sendMessage(session, new ErrorMessage("error", error, message, deviceId));
    }

    // Convierte un objeto a texto JSON.
    private String writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to serialize WebSocket payload", exception);
        }
    }
}

