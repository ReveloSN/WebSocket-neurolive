package com.neurolive.realtime.dto;

import com.neurolive.realtime.model.TelemetrySnapshot;
import java.util.List;

// Representa la última telemetría y el historial reciente.
public record LatestTelemetryResponse(
        String deviceId,
        TelemetrySnapshot latestTelemetry,
        List<TelemetrySnapshot> recentHistory
) {
}

