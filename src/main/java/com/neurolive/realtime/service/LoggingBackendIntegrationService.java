package com.neurolive.realtime.service;

import com.neurolive.realtime.config.RealtimeProperties;
import com.neurolive.realtime.dto.AckMessage;
import com.neurolive.realtime.dto.DeviceCommandMessage;
import com.neurolive.realtime.model.TelemetrySnapshot;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Simula la integración futura mediante logs claros.
@Service
public class LoggingBackendIntegrationService implements BackendIntegrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingBackendIntegrationService.class);

    private final RealtimeProperties realtimeProperties;

    // Recibe la configuración del backend externo.
    public LoggingBackendIntegrationService(RealtimeProperties realtimeProperties) {
        this.realtimeProperties = realtimeProperties;
    }

    // Simula el reenvío de telemetría al backend principal.
    @Override
    public void forwardTelemetry(TelemetrySnapshot telemetrySnapshot) {
        LOGGER.info("Mock backend telemetry forward: baseUrl={}, deviceId={}, bpm={}, spo2={}",
                realtimeProperties.getIntegration().getBaseUrl(),
                telemetrySnapshot.deviceId(),
                telemetrySnapshot.bpm(),
                telemetrySnapshot.spo2());
    }

    // Simula la notificación de autenticación.
    @Override
    public void notifyDeviceAuthenticated(String deviceId, Instant occurredAt) {
        LOGGER.info("Mock backend auth notification: baseUrl={}, deviceId={}, occurredAt={}",
                realtimeProperties.getIntegration().getBaseUrl(),
                deviceId,
                occurredAt);
    }

    // Simula la notificación de heartbeat.
    @Override
    public void notifyHeartbeat(String deviceId, Instant occurredAt) {
        LOGGER.debug("Mock backend heartbeat notification: baseUrl={}, deviceId={}, occurredAt={}",
                realtimeProperties.getIntegration().getBaseUrl(),
                deviceId,
                occurredAt);
    }

    // Simula la notificación de desconexión.
    @Override
    public void notifyDeviceDisconnected(String deviceId, String reason, Instant occurredAt) {
        LOGGER.warn("Mock backend disconnection notification: baseUrl={}, deviceId={}, reason={}, occurredAt={}",
                realtimeProperties.getIntegration().getBaseUrl(),
                deviceId,
                reason,
                occurredAt);
    }

    // Simula la notificación de comando enviado.
    @Override
    public void notifyCommandSent(String deviceId, DeviceCommandMessage commandMessage, Instant occurredAt) {
        LOGGER.info("Mock backend command sent notification: baseUrl={}, deviceId={}, commandId={}, action={}, occurredAt={}",
                realtimeProperties.getIntegration().getBaseUrl(),
                deviceId,
                commandMessage.commandId(),
                commandMessage.action(),
                occurredAt);
    }

    // Simula la notificación de acuse recibido.
    @Override
    public void notifyCommandAcknowledged(AckMessage ackMessage, Instant occurredAt) {
        LOGGER.info("Mock backend ack notification: baseUrl={}, deviceId={}, commandId={}, status={}, occurredAt={}",
                realtimeProperties.getIntegration().getBaseUrl(),
                ackMessage.deviceId(),
                ackMessage.commandId(),
                ackMessage.status(),
                occurredAt);
    }
}

