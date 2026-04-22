package com.neurolive.realtime.dto;

// Representa el mensaje de autenticación del dispositivo.
public record AuthMessage(String type, String deviceId, String token) {
}

