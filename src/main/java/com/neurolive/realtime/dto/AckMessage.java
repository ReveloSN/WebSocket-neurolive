package com.neurolive.realtime.dto;

// Representa el acuse de un comando aplicado.
public record AckMessage(String type, String deviceId, String commandId, String status) {
}

