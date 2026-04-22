package com.neurolive.realtime.service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

// Administra las sesiones autenticadas activas por dispositivo.
@Service
public class DeviceSessionRegistry {

    private final ConcurrentHashMap<String, WebSocketSession> sessionsByDeviceId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> deviceIdBySessionId = new ConcurrentHashMap<>();

    // Registra o reemplaza la sesión activa del dispositivo.
    public Optional<WebSocketSession> registerSession(String deviceId, WebSocketSession session) {
        WebSocketSession previousSession = sessionsByDeviceId.put(deviceId, session);
        deviceIdBySessionId.put(session.getId(), deviceId);
        if (previousSession != null && !previousSession.getId().equals(session.getId())) {
            deviceIdBySessionId.remove(previousSession.getId());
            return Optional.of(previousSession);
        }
        return Optional.empty();
    }

    // Obtiene la sesión activa de un dispositivo.
    public Optional<WebSocketSession> findSession(String deviceId) {
        return Optional.ofNullable(sessionsByDeviceId.get(deviceId));
    }

    // Obtiene el deviceId asociado a un sessionId.
    public Optional<String> findDeviceIdBySessionId(String sessionId) {
        return Optional.ofNullable(deviceIdBySessionId.get(sessionId));
    }

    // Elimina una sesión usando el deviceId.
    public Optional<WebSocketSession> removeByDeviceId(String deviceId) {
        WebSocketSession removedSession = sessionsByDeviceId.remove(deviceId);
        if (removedSession != null) {
            deviceIdBySessionId.remove(removedSession.getId());
        }
        return Optional.ofNullable(removedSession);
    }

    // Elimina una sesión usando el sessionId.
    public Optional<WebSocketSession> removeBySessionId(String sessionId) {
        String deviceId = deviceIdBySessionId.remove(sessionId);
        if (deviceId == null) {
            return Optional.empty();
        }
        WebSocketSession session = sessionsByDeviceId.get(deviceId);
        if (session != null && session.getId().equals(sessionId)) {
            sessionsByDeviceId.remove(deviceId, session);
            return Optional.of(session);
        }
        return Optional.empty();
    }

    // Lista los dispositivos con sesión activa.
    public List<String> listConnectedDeviceIds() {
        return List.copyOf(sessionsByDeviceId.keySet());
    }

    // Verifica si el dispositivo tiene sesión activa.
    public boolean isConnected(String deviceId) {
        return sessionsByDeviceId.containsKey(deviceId);
    }
}

