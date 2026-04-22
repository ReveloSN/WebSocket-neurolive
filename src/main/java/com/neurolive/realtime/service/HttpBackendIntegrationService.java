package com.neurolive.realtime.service;

import com.neurolive.realtime.config.RealtimeProperties;
import com.neurolive.realtime.dto.AckMessage;
import com.neurolive.realtime.dto.DeviceCommandMessage;
import com.neurolive.realtime.model.TelemetrySnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

// Implementacion real: reenvia eventos al backend principal via HTTP.
@Service
public class HttpBackendIntegrationService implements BackendIntegrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpBackendIntegrationService.class);

    private final WebClient backendWebClient;
    private final Duration integrationTimeout;

    // Recibe el cliente HTTP y el timeout configurado.
    public HttpBackendIntegrationService(WebClient backendWebClient,
                                         RealtimeProperties realtimeProperties) {
        this.backendWebClient = backendWebClient;
        this.integrationTimeout = Duration.ofMillis(realtimeProperties.getIntegration().getTimeoutMs());
    }

    // Reenvia telemetria biometrica al backend para persistencia y analisis.
    @Override
    public void forwardTelemetry(TelemetrySnapshot snapshot) {
        backendWebClient.post()
                .uri("/internal/telemetry")
                .bodyValue(Map.of(
                        "deviceId", snapshot.deviceId(),
                        "bpm", snapshot.bpm(),
                        "spo2", snapshot.spo2(),
                        "sensorConnected", snapshot.sensorConnected(),
                        "deviceTimestamp", snapshot.deviceTimestamp() != null ? snapshot.deviceTimestamp() : 0L,
                        "receivedAt", snapshot.receivedAt().toString()
                ))
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(integrationTimeout)
                .doOnError(exception -> LOGGER.warn(
                        "Telemetry forward failed deviceId={}: {}",
                        snapshot.deviceId(),
                        exception.getMessage()
                ))
                .onErrorComplete()
                .subscribe();
    }

    // Notifica al backend que el dispositivo se autentico.
    @Override
    public void notifyDeviceAuthenticated(String deviceId, Instant occurredAt) {
        backendWebClient.post()
                .uri("/internal/devices/{id}/authenticated", deviceId)
                .bodyValue(Map.of("occurredAt", occurredAt.toString()))
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(integrationTimeout)
                .doOnError(exception -> LOGGER.warn(
                        "Auth notify failed deviceId={}: {}",
                        deviceId,
                        exception.getMessage()
                ))
                .onErrorComplete()
                .subscribe();
    }

    // Registra heartbeat solo en este servicio por volumen.
    @Override
    public void notifyHeartbeat(String deviceId, Instant occurredAt) {
        LOGGER.debug("Heartbeat received deviceId={} occurredAt={}", deviceId, occurredAt);
    }

    // Notifica al backend que el dispositivo se desconecto.
    @Override
    public void notifyDeviceDisconnected(String deviceId, String reason, Instant occurredAt) {
        backendWebClient.post()
                .uri("/internal/devices/{id}/disconnected", deviceId)
                .bodyValue(Map.of(
                        "reason", reason != null ? reason : "unknown",
                        "occurredAt", occurredAt.toString()
                ))
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(integrationTimeout)
                .doOnError(exception -> LOGGER.warn(
                        "Disconnect notify failed deviceId={}: {}",
                        deviceId,
                        exception.getMessage()
                ))
                .onErrorComplete()
                .subscribe();
    }

    // Registra que un comando fue enviado al dispositivo.
    @Override
    public void notifyCommandSent(String deviceId, DeviceCommandMessage command, Instant occurredAt) {
        LOGGER.info("Command sent deviceId={} commandId={} action={}", deviceId, command.commandId(), command.action());
    }

    // Registra que el dispositivo acuso recibo del comando.
    @Override
    public void notifyCommandAcknowledged(AckMessage ackMessage, Instant occurredAt) {
        LOGGER.info(
                "Command ack deviceId={} commandId={} status={}",
                ackMessage.deviceId(),
                ackMessage.commandId(),
                ackMessage.status()
        );
    }
}
