package com.neurolive.realtime.dto;

import com.neurolive.realtime.model.DeviceConnectionState;
import java.time.Instant;

// Representa un dispositivo conectado para la API REST.
public record ConnectedDeviceResponse(
        String deviceId,
        DeviceConnectionState state,
        Instant connectedAt,
        Instant lastActivityAt,
        Instant lastHeartbeatAt
) {
}

