package com.neurolive.realtime.dto;

// Representa un comando saliente hacia el ESP32.
public record DeviceCommandMessage(String type, String commandId, String action, Object payload) {
}

