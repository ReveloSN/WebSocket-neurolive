package com.neurolive.realtime.service;

import com.neurolive.realtime.dto.AckMessage;
import com.neurolive.realtime.dto.DeviceCommandMessage;
import com.neurolive.realtime.model.RealtimeEvent;
import com.neurolive.realtime.model.TelemetrySnapshot;
import org.springframework.stereotype.Component;

// Reenvía eventos internos hacia la capa de integración futura.
@Component
public class BackendIntegrationObserver implements RealtimeEventObserver {

    private final BackendIntegrationService backendIntegrationService;

    // Recibe el servicio placeholder de integración.
    public BackendIntegrationObserver(BackendIntegrationService backendIntegrationService) {
        this.backendIntegrationService = backendIntegrationService;
    }

    // Procesa cada evento y llama al punto de integración correspondiente.
    @Override
    public void onEvent(RealtimeEvent event) {
        switch (event.type()) {
            case DEVICE_AUTHENTICATED -> backendIntegrationService.notifyDeviceAuthenticated(event.deviceId(), event.occurredAt());
            case HEARTBEAT_RECEIVED -> backendIntegrationService.notifyHeartbeat(event.deviceId(), event.occurredAt());
            case DEVICE_DISCONNECTED -> backendIntegrationService.notifyDeviceDisconnected(
                    event.deviceId(),
                    event.payload() instanceof String reason ? reason : "unknown",
                    event.occurredAt()
            );
            case TELEMETRY_RECEIVED -> {
                if (event.payload() instanceof TelemetrySnapshot telemetrySnapshot) {
                    backendIntegrationService.forwardTelemetry(telemetrySnapshot);
                }
            }
            case COMMAND_SENT -> {
                if (event.payload() instanceof DeviceCommandMessage commandMessage) {
                    backendIntegrationService.notifyCommandSent(event.deviceId(), commandMessage, event.occurredAt());
                }
            }
            case COMMAND_ACKNOWLEDGED -> {
                if (event.payload() instanceof AckMessage ackMessage) {
                    backendIntegrationService.notifyCommandAcknowledged(ackMessage, event.occurredAt());
                }
            }
            default -> {
                // No requiere acción adicional.
            }
        }
    }
}

