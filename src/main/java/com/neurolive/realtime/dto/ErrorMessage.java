package com.neurolive.realtime.dto;

// Representa un error enviado por WebSocket.
public record ErrorMessage(String type, String error, String message, String deviceId) {
}

