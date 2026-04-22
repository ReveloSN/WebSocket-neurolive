package com.neurolive.realtime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neurolive.realtime.dto.CommandDispatchResponse;
import com.neurolive.realtime.dto.DeviceCommandMessage;
import com.neurolive.realtime.dto.LightCommandRequest;
import com.neurolive.realtime.exception.DeviceNotConnectedException;
import com.neurolive.realtime.model.DeviceCommand;
import com.neurolive.realtime.model.LightCommand;
import com.neurolive.realtime.model.LightCommandPayload;
import com.neurolive.realtime.model.RealtimeEvent;
import com.neurolive.realtime.model.RealtimeEventType;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

// Envía comandos desde el servicio hacia los dispositivos conectados.
@Service
public class DeviceCommandService {

    private final DeviceSessionRegistry deviceSessionRegistry;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final ObjectMapper objectMapper;

    // Recibe las dependencias para el envío de comandos.
    public DeviceCommandService(DeviceSessionRegistry deviceSessionRegistry,
                                RealtimeEventPublisher realtimeEventPublisher,
                                ObjectMapper objectMapper) {
        this.deviceSessionRegistry = deviceSessionRegistry;
        this.realtimeEventPublisher = realtimeEventPublisher;
        this.objectMapper = objectMapper;
    }

    // Construye y envía un comando de luz al ESP32.
    public CommandDispatchResponse sendLightCommand(String deviceId, LightCommandRequest request) {
        LightCommand command = buildLightCommand(deviceId, request);
        sendCommand(deviceId, command);
        return new CommandDispatchResponse(deviceId, command.commandId(), command.action(), "sent", Instant.now());
    }

    // Construye el objeto de comando concreto.
    private LightCommand buildLightCommand(String deviceId, LightCommandRequest request) {
        return new LightCommand(
                deviceId,
                UUID.randomUUID().toString(),
                new LightCommandPayload(request.color(), request.intensity(), request.mode())
        );
    }

    // Serializa y envía cualquier comando soportado.
    private void sendCommand(String deviceId, DeviceCommand command) {
        WebSocketSession session = deviceSessionRegistry.findSession(deviceId)
                .filter(WebSocketSession::isOpen)
                .orElseThrow(() -> new DeviceNotConnectedException("Device " + deviceId + " is not connected"));
        DeviceCommandMessage outboundMessage = command.toMessage();
        try {
            String payload = objectMapper.writeValueAsString(outboundMessage);
            synchronized (session) {
                session.sendMessage(new TextMessage(payload));
            }
            realtimeEventPublisher.publish(new RealtimeEvent(
                    RealtimeEventType.COMMAND_SENT,
                    deviceId,
                    Instant.now(),
                    outboundMessage
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to serialize command payload");
        } catch (IOException exception) {
            throw new DeviceNotConnectedException("Failed to deliver command to device " + deviceId);
        }
    }
}

