package com.neurolive.realtime.service;

import com.neurolive.realtime.config.RealtimeProperties;
import com.neurolive.realtime.model.RealtimeEvent;
import com.neurolive.realtime.model.RealtimeEventType;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

// Revisa sesiones inactivas y dispara desconexión por timeout.
@Service
public class HeartbeatMonitorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HeartbeatMonitorService.class);

    private final RealtimeProperties realtimeProperties;
    private final DeviceSessionRegistry deviceSessionRegistry;
    private final ConnectionStatusService connectionStatusService;
    private final RealtimeEventPublisher realtimeEventPublisher;

    // Recibe las dependencias para vigilar actividad.
    public HeartbeatMonitorService(RealtimeProperties realtimeProperties,
                                   DeviceSessionRegistry deviceSessionRegistry,
                                   ConnectionStatusService connectionStatusService,
                                   RealtimeEventPublisher realtimeEventPublisher) {
        this.realtimeProperties = realtimeProperties;
        this.deviceSessionRegistry = deviceSessionRegistry;
        this.connectionStatusService = connectionStatusService;
        this.realtimeEventPublisher = realtimeEventPublisher;
    }

    // Registra un heartbeat valido en el estado del dispositivo.
    public void recordHeartbeat(String deviceId) {
        Instant now = Instant.now();
        connectionStatusService.recordHeartbeat(deviceId, now);
        realtimeEventPublisher.publish(new RealtimeEvent(
                RealtimeEventType.HEARTBEAT_RECEIVED,
                deviceId,
                now,
                null
        ));
    }

    // Detecta dispositivos sin actividad dentro del tiempo permitido.
    @Scheduled(fixedDelayString = "${realtime.heartbeat.check-interval-millis:5000}")
    public void checkInactiveDevices() {
        Instant now = Instant.now();
        Map<String, Instant> lastActivitySnapshot = connectionStatusService.getLastActivitySnapshot();
        List<String> connectedDeviceIds = deviceSessionRegistry.listConnectedDeviceIds();
        for (String deviceId : connectedDeviceIds) {
            Instant lastActivity = lastActivitySnapshot.get(deviceId);
            if (isExpired(lastActivity, now)) {
                deviceSessionRegistry.findSession(deviceId).ifPresent(session -> closeExpiredSession(deviceId, session, lastActivity));
            }
        }
    }

    // Evalúa si el dispositivo ya excedió el timeout.
    private boolean isExpired(Instant lastActivity, Instant now) {
        if (lastActivity == null) {
            return true;
        }
        Duration inactivity = Duration.between(lastActivity, now);
        return inactivity.getSeconds() >= realtimeProperties.getHeartbeat().getTimeoutSeconds();
    }

    // Cierra la sesión vencida para forzar la desconexión controlada.
    private void closeExpiredSession(String deviceId, WebSocketSession session, Instant lastActivity) {
        if (!session.isOpen()) {
            return;
        }
        try {
            LOGGER.warn("Closing inactive device session: deviceId={}, sessionId={}, lastActivity={}",
                    deviceId,
                    session.getId(),
                    lastActivity);
            session.close(CloseStatus.SESSION_NOT_RELIABLE);
        } catch (IOException exception) {
            LOGGER.error("Failed to close inactive session for device {}", deviceId, exception);
        }
    }
}
