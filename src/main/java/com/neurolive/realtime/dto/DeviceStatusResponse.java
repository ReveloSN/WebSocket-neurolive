package com.neurolive.realtime.dto;

import com.neurolive.realtime.model.DeviceConnectionState;
import java.time.Instant;

// Representa el estado detallado de un dispositivo.
public record DeviceStatusResponse(
        String deviceId,
        DeviceConnectionState state,
        boolean connected,
        Instant connectedAt,
        Instant lastActivityAt,
        Instant lastHeartbeatAt,
        Instant disconnectedAt,
        String disconnectReason
) {
}

