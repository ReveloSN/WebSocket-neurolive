package com.neurolive.realtime.model;

// Representa la carga útil de un comando de luz.
public record LightCommandPayload(String color, int intensity, String mode) {
}

