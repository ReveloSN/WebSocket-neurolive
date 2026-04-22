package com.neurolive.realtime.service;

import com.neurolive.realtime.model.DeviceConnectionState;
import com.neurolive.realtime.model.DeviceStatusSnapshot;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

// Mantiene el estado y la última actividad por dispositivo.
@Service
public class ConnectionStatusService {

    private final ConcurrentHashMap<String, DeviceConnectionState> states = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> connectedAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lastActivityAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lastHeartbeatAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> disconnectedAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> disconnectReasons = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> activeSessionIds = new ConcurrentHashMap<>();

    // Marca un dispositivo como autenticado y conectado.
    public void markAuthenticated(String deviceId, String sessionId) {
        Instant now = Instant.now();
        states.put(deviceId, DeviceConnectionState.CONNECTED);
        connectedAt.put(deviceId, now);
        lastActivityAt.put(deviceId, now);
        disconnectedAt.remove(deviceId);
        disconnectReasons.remove(deviceId);
        activeSessionIds.put(deviceId, sessionId);
    }

    // Registra actividad derivada de telemetría.
    public void recordTelemetry(String deviceId, Instant timestamp) {
        lastActivityAt.put(deviceId, timestamp);
        states.put(deviceId, DeviceConnectionState.CONNECTED);
    }

    // Registra actividad derivada de heartbeat.
    public void recordHeartbeat(String deviceId, Instant timestamp) {
        lastHeartbeatAt.put(deviceId, timestamp);
        lastActivityAt.put(deviceId, timestamp);
        states.put(deviceId, DeviceConnectionState.CONNECTED);
    }

    // Marca una desconexión si corresponde a la sesión actual.
    public boolean markDisconnected(String deviceId, String sessionId, String reason) {
        String currentSessionId = activeSessionIds.get(deviceId);
        if (currentSessionId != null && sessionId != null && !currentSessionId.equals(sessionId)) {
            return false;
        }
        states.put(deviceId, DeviceConnectionState.DISCONNECTED);
        disconnectedAt.put(deviceId, Instant.now());
        disconnectReasons.put(deviceId, reason);
        if (sessionId == null) {
            activeSessionIds.remove(deviceId);
        } else {
            activeSessionIds.remove(deviceId, sessionId);
        }
        return true;
    }

    // Retorna el estado compuesto de un dispositivo.
    public DeviceStatusSnapshot getStatus(String deviceId) {
        DeviceConnectionState state = states.getOrDefault(deviceId, DeviceConnectionState.NEVER_CONNECTED);
        String sessionId = activeSessionIds.get(deviceId);
        return new DeviceStatusSnapshot(
                deviceId,
                state,
                state == DeviceConnectionState.CONNECTED && sessionId != null,
                sessionId,
                connectedAt.get(deviceId),
                lastActivityAt.get(deviceId),
                lastHeartbeatAt.get(deviceId),
                disconnectedAt.get(deviceId),
                disconnectReasons.get(deviceId)
        );
    }

    // Retorna una copia de la última actividad conocida.
    public Map<String, Instant> getLastActivitySnapshot() {
        return Map.copyOf(lastActivityAt);
    }

    // Retorna todos los dispositivos observados por el servicio.
    public Set<String> getKnownDeviceIds() {
        Set<String> deviceIds = new HashSet<>();
        deviceIds.addAll(states.keySet());
        deviceIds.addAll(activeSessionIds.keySet());
        return Set.copyOf(deviceIds);
    }
}

