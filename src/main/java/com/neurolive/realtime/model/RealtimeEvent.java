package com.neurolive.realtime.model;

import java.time.Instant;

// Representa un evento publicado dentro del servicio.
public record RealtimeEvent(RealtimeEventType type, String deviceId, Instant occurredAt, Object payload) {
}
