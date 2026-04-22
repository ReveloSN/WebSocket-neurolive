package com.neurolive.realtime.model;

// Define los tipos de eventos internos del servicio.
public enum RealtimeEventType {
    DEVICE_AUTHENTICATED,
    TELEMETRY_RECEIVED,
    HEARTBEAT_RECEIVED,
    DEVICE_DISCONNECTED,
    COMMAND_SENT,
    COMMAND_ACKNOWLEDGED
}

