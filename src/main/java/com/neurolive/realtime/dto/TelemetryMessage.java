package com.neurolive.realtime.dto;

// Representa un envío de telemetría biométrica.
public record TelemetryMessage(
        String type,
        String deviceId,
        Long timestamp,
        Integer bpm,
        Integer spo2,
        Boolean sensorConnected
) {
}

