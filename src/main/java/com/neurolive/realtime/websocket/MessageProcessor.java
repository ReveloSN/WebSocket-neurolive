package com.neurolive.realtime.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.socket.WebSocketSession;

// Define el contrato de un procesador de mensajes.
public interface MessageProcessor {

    // Retorna el tipo de mensaje soportado.
    String supportedType();

    // Procesa el mensaje recibido para una sesion.
    void process(JsonNode payload, WebSocketSession session);
}

