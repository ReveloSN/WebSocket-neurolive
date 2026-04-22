package com.neurolive.realtime.dto;

import java.time.Instant;

// Representa la respuesta REST al despachar un comando.
public record CommandDispatchResponse(
        String deviceId,
        String commandId,
        String action,
        String status,
        Instant sentAt
) {
}

