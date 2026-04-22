package com.neurolive.realtime.dto;

// Representa un heartbeat enviado por el dispositivo.
public record HeartbeatMessage(String type, String deviceId, Long timestamp) {
}

