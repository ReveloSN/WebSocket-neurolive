package com.neurolive.realtime.dto;

import java.time.Instant;

// Representa un error estándar para la API REST.
public record ApiErrorResponse(Instant timestamp, int status, String error, String message) {
}

