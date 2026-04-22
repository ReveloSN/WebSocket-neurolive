package com.neurolive.realtime.model;

import java.time.Instant;

// Representa una telemetría almacenada en memoria.
public record TelemetrySnapshot(
        String deviceId,
        Instant receivedAt,
        Long deviceTimestamp,
        int bpm,
        int spo2,
        boolean sensorConnected
) {
}

