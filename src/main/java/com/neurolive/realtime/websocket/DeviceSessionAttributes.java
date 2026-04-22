package com.neurolive.realtime.websocket;

import org.springframework.web.socket.WebSocketSession;

// Centraliza los atributos usados en la sesion WebSocket.
public final class DeviceSessionAttributes {

    public static final String AUTHENTICATED_DEVICE_ID = "authenticatedDeviceId";

    // Evita instancias de la clase utilitaria.
    private DeviceSessionAttributes() {
    }

    // Obtiene el deviceId autenticado en la sesion.
    public static String getAuthenticatedDeviceId(WebSocketSession session) {
        Object value = session.getAttributes().get(AUTHENTICATED_DEVICE_ID);
        return value instanceof String deviceId ? deviceId : null;
    }

    // Guarda el deviceId autenticado en la sesion.
    public static void setAuthenticatedDeviceId(WebSocketSession session, String deviceId) {
        session.getAttributes().put(AUTHENTICATED_DEVICE_ID, deviceId);
    }
}

