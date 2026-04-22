package com.neurolive.realtime.controller;

import com.neurolive.realtime.dto.CommandDispatchResponse;
import com.neurolive.realtime.dto.ConnectedDeviceResponse;
import com.neurolive.realtime.dto.DeviceStatusResponse;
import com.neurolive.realtime.dto.LatestTelemetryResponse;
import com.neurolive.realtime.dto.LightCommandRequest;
import com.neurolive.realtime.model.DeviceStatusSnapshot;
import com.neurolive.realtime.service.ConnectionStatusService;
import com.neurolive.realtime.service.DeviceCommandService;
import com.neurolive.realtime.service.DeviceSessionRegistry;
import com.neurolive.realtime.service.TelemetryIngestionService;
import jakarta.validation.Valid;
import java.util.Comparator;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Expone endpoints minimos para pruebas e integracion.
@RestController
@RequestMapping("/api/devices")
public class DeviceManagementController {

    private final DeviceSessionRegistry deviceSessionRegistry;
    private final ConnectionStatusService connectionStatusService;
    private final TelemetryIngestionService telemetryIngestionService;
    private final DeviceCommandService deviceCommandService;

    // Recibe los servicios necesarios para la API REST.
    public DeviceManagementController(DeviceSessionRegistry deviceSessionRegistry,
                                      ConnectionStatusService connectionStatusService,
                                      TelemetryIngestionService telemetryIngestionService,
                                      DeviceCommandService deviceCommandService) {
        this.deviceSessionRegistry = deviceSessionRegistry;
        this.connectionStatusService = connectionStatusService;
        this.telemetryIngestionService = telemetryIngestionService;
        this.deviceCommandService = deviceCommandService;
    }

    // Lista los dispositivos actualmente conectados.
    @GetMapping("/connected")
    public List<ConnectedDeviceResponse> getConnectedDevices() {
        return deviceSessionRegistry.listConnectedDeviceIds().stream()
                .map(connectionStatusService::getStatus)
                .sorted(Comparator.comparing(DeviceStatusSnapshot::deviceId))
                .map(this::buildConnectedDeviceResponse)
                .toList();
    }

    // Retorna el estado actual de un dispositivo.
    @GetMapping("/{deviceId}/status")
    public DeviceStatusResponse getDeviceStatus(@PathVariable String deviceId) {
        return buildDeviceStatusResponse(connectionStatusService.getStatus(deviceId));
    }

    // Retorna la ultima telemetria y el historial corto del dispositivo.
    @GetMapping("/{deviceId}/telemetry/latest")
    public LatestTelemetryResponse getLatestTelemetry(@PathVariable String deviceId) {
        return new LatestTelemetryResponse(
                deviceId,
                telemetryIngestionService.getLatestTelemetry(deviceId).orElse(null),
                telemetryIngestionService.getRecentHistory(deviceId)
        );
    }

    // Envia un comando de luz al dispositivo conectado.
    @PostMapping("/{deviceId}/commands/light")
    public CommandDispatchResponse sendLightCommand(@PathVariable String deviceId,
                                                    @Valid @RequestBody LightCommandRequest request) {
        return deviceCommandService.sendLightCommand(deviceId, request);
    }

    // Convierte el estado interno a una respuesta compacta.
    private ConnectedDeviceResponse buildConnectedDeviceResponse(DeviceStatusSnapshot snapshot) {
        return new ConnectedDeviceResponse(
                snapshot.deviceId(),
                snapshot.state(),
                snapshot.connectedAt(),
                snapshot.lastActivityAt(),
                snapshot.lastHeartbeatAt()
        );
    }

    // Convierte el estado interno a la respuesta detallada.
    private DeviceStatusResponse buildDeviceStatusResponse(DeviceStatusSnapshot snapshot) {
        return new DeviceStatusResponse(
                snapshot.deviceId(),
                snapshot.state(),
                snapshot.connected(),
                snapshot.connectedAt(),
                snapshot.lastActivityAt(),
                snapshot.lastHeartbeatAt(),
                snapshot.disconnectedAt(),
                snapshot.disconnectReason()
        );
    }
}
