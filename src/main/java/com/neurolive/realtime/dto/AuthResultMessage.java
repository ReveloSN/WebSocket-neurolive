package com.neurolive.realtime.dto;

// Representa la respuesta de autenticación al dispositivo.
public record AuthResultMessage(String type, String deviceId, boolean authenticated, String message) {
}

