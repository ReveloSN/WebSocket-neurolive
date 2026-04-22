package com.neurolive.realtime.dto;

// Representa los campos comunes de un mensaje entrante.
public record BaseMessage(String type, String deviceId) {
}

