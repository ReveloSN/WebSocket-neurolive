package com.neurolive.realtime.model;

import java.time.Instant;

// Representa el estado interno completo del dispositivo.
public record DeviceStatusSnapshot(
        String deviceId,
        DeviceConnectionState state,
        boolean connected,
        String sessionId,
        Instant connectedAt,
        Instant lastActivityAt,
        Instant lastHeartbeatAt,
        Instant disconnectedAt,
        String disconnectReason
) {
}

