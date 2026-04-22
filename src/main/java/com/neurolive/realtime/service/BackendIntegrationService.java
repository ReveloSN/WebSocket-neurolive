package com.neurolive.realtime.service;

import com.neurolive.realtime.dto.AckMessage;
import com.neurolive.realtime.dto.DeviceCommandMessage;
import com.neurolive.realtime.model.TelemetrySnapshot;
import java.time.Instant;

// Define los puntos de integración futura con el backend principal.
public interface BackendIntegrationService {

    // Reenvía una telemetría al backend principal.
    void forwardTelemetry(TelemetrySnapshot telemetrySnapshot);

    // Notifica que un dispositivo fue autenticado.
    void notifyDeviceAuthenticated(String deviceId, Instant occurredAt);

    // Notifica que llegó un heartbeat útil.
    void notifyHeartbeat(String deviceId, Instant occurredAt);

    // Notifica que el dispositivo quedó desconectado.
    void notifyDeviceDisconnected(String deviceId, String reason, Instant occurredAt);

    // Notifica que el servicio envió un comando al dispositivo.
    void notifyCommandSent(String deviceId, DeviceCommandMessage commandMessage, Instant occurredAt);

    // Notifica el acuse recibido desde el dispositivo.
    void notifyCommandAcknowledged(AckMessage ackMessage, Instant occurredAt);
}

