package com.neurolive.realtime.service;

import com.neurolive.realtime.config.RealtimeProperties;
import com.neurolive.realtime.dto.TelemetryMessage;
import com.neurolive.realtime.exception.InvalidDeviceMessageException;
import com.neurolive.realtime.model.RealtimeEvent;
import com.neurolive.realtime.model.RealtimeEventType;
import com.neurolive.realtime.model.TelemetrySnapshot;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

// Recibe y conserva la telemetría reciente en memoria.
@Service
public class TelemetryIngestionService {

    private final RealtimeProperties realtimeProperties;
    private final ConnectionStatusService connectionStatusService;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final ConcurrentHashMap<String, TelemetrySnapshot> latestTelemetryByDeviceId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ArrayDeque<TelemetrySnapshot>> telemetryHistoryByDeviceId = new ConcurrentHashMap<>();

    // Recibe los colaboradores necesarios para almacenar telemetría.
    public TelemetryIngestionService(RealtimeProperties realtimeProperties,
                                     ConnectionStatusService connectionStatusService,
                                     RealtimeEventPublisher realtimeEventPublisher) {
        this.realtimeProperties = realtimeProperties;
        this.connectionStatusService = connectionStatusService;
        this.realtimeEventPublisher = realtimeEventPublisher;
    }

    // Valida y almacena una telemetría entrante.
    public TelemetrySnapshot storeTelemetry(TelemetryMessage telemetryMessage) {
        validateTelemetryMessage(telemetryMessage);
        Instant receivedAt = Instant.now();
        TelemetrySnapshot snapshot = new TelemetrySnapshot(
                telemetryMessage.deviceId(),
                receivedAt,
                telemetryMessage.timestamp(),
                telemetryMessage.bpm(),
                telemetryMessage.spo2(),
                telemetryMessage.sensorConnected()
        );
        latestTelemetryByDeviceId.put(telemetryMessage.deviceId(), snapshot);
        appendHistory(telemetryMessage.deviceId(), snapshot);
        connectionStatusService.recordTelemetry(telemetryMessage.deviceId(), receivedAt);
        realtimeEventPublisher.publish(new RealtimeEvent(
                RealtimeEventType.TELEMETRY_RECEIVED,
                telemetryMessage.deviceId(),
                receivedAt,
                snapshot
        ));
        return snapshot;
    }

    // Obtiene la última telemetría conocida.
    public Optional<TelemetrySnapshot> getLatestTelemetry(String deviceId) {
        return Optional.ofNullable(latestTelemetryByDeviceId.get(deviceId));
    }

    // Obtiene el historial corto almacenado en memoria.
    public List<TelemetrySnapshot> getRecentHistory(String deviceId) {
        ArrayDeque<TelemetrySnapshot> history = telemetryHistoryByDeviceId.get(deviceId);
        if (history == null) {
            return List.of();
        }
        synchronized (history) {
            return List.copyOf(history);
        }
    }

    // Valida los campos requeridos de la telemetría.
    private void validateTelemetryMessage(TelemetryMessage telemetryMessage) {
        if (telemetryMessage == null) {
            throw new InvalidDeviceMessageException("Telemetry payload is required");
        }
        if (telemetryMessage.deviceId() == null || telemetryMessage.deviceId().isBlank()) {
            throw new InvalidDeviceMessageException("Telemetry deviceId is required");
        }
        if (telemetryMessage.bpm() == null || telemetryMessage.bpm() < 0) {
            throw new InvalidDeviceMessageException("Telemetry bpm must be a non-negative integer");
        }
        if (telemetryMessage.spo2() == null || telemetryMessage.spo2() < 0 || telemetryMessage.spo2() > 100) {
            throw new InvalidDeviceMessageException("Telemetry spo2 must be between 0 and 100");
        }
        if (telemetryMessage.sensorConnected() == null) {
            throw new InvalidDeviceMessageException("Telemetry sensorConnected is required");
        }
    }

    // Agrega la muestra a un buffer reciente limitado.
    private void appendHistory(String deviceId, TelemetrySnapshot snapshot) {
        ArrayDeque<TelemetrySnapshot> history = telemetryHistoryByDeviceId.computeIfAbsent(deviceId, key -> new ArrayDeque<>());
        synchronized (history) {
            int historyLimit = Math.max(realtimeProperties.getTelemetry().getHistoryLimit(), 1);
            if (history.size() >= historyLimit) {
                history.removeFirst();
            }
            history.addLast(snapshot);
        }
    }
}

