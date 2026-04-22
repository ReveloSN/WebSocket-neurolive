package com.neurolive.realtime.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neurolive.realtime.dto.BaseMessage;
import com.neurolive.realtime.exception.DeviceAuthenticationException;
import com.neurolive.realtime.exception.InvalidDeviceMessageException;
import com.neurolive.realtime.model.RealtimeEvent;
import com.neurolive.realtime.model.RealtimeEventType;
import com.neurolive.realtime.service.ConnectionStatusService;
import com.neurolive.realtime.service.DeviceSessionRegistry;
import com.neurolive.realtime.service.RealtimeEventPublisher;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

// Maneja la comunicacion raw entre el servicio y el ESP32.
@Component
public class IoTWebSocketHandler extends TextWebSocketHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(IoTWebSocketHandler.class);

    private final ObjectMapper objectMapper;
    private final MessageProcessorFactory messageProcessorFactory;
    private final DeviceSessionRegistry deviceSessionRegistry;
    private final ConnectionStatusService connectionStatusService;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final WebSocketJsonSender webSocketJsonSender;

    // Recibe las dependencias del canal WebSocket.
    public IoTWebSocketHandler(ObjectMapper objectMapper,
                               MessageProcessorFactory messageProcessorFactory,
                               DeviceSessionRegistry deviceSessionRegistry,
                               ConnectionStatusService connectionStatusService,
                               RealtimeEventPublisher realtimeEventPublisher,
                               WebSocketJsonSender webSocketJsonSender) {
        this.objectMapper = objectMapper;
        this.messageProcessorFactory = messageProcessorFactory;
        this.deviceSessionRegistry = deviceSessionRegistry;
        this.connectionStatusService = connectionStatusService;
        this.realtimeEventPublisher = realtimeEventPublisher;
        this.webSocketJsonSender = webSocketJsonSender;
    }

    // Registra la apertura inicial de la conexion.
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        LOGGER.info("WebSocket connection established: sessionId={}, remoteAddress={}", session.getId(), session.getRemoteAddress());
    }

    // Procesa cada mensaje textual recibido desde el dispositivo.
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            JsonNode payload = parsePayload(message.getPayload());
            BaseMessage baseMessage = objectMapper.treeToValue(payload, BaseMessage.class);
            validateBaseMessage(baseMessage);
            messageProcessorFactory.getProcessor(baseMessage.type()).process(payload, session);
        } catch (InvalidDeviceMessageException | DeviceAuthenticationException exception) {
            LOGGER.warn("Closing session {} due to client error: {}", session.getId(), exception.getMessage());
            sendErrorAndClose(session, CloseStatus.POLICY_VIOLATION, "client_error", exception.getMessage());
        } catch (Exception exception) {
            LOGGER.error("Unexpected error while handling WebSocket message for session {}", session.getId(), exception);
            sendErrorAndClose(session, CloseStatus.SERVER_ERROR, "server_error", "Unexpected internal server error");
        }
    }

    // Limpia el estado cuando una conexion se cierra.
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String deviceId = resolveAuthenticatedDeviceId(session).orElse(null);
        deviceSessionRegistry.removeBySessionId(session.getId());
        if (deviceId != null && connectionStatusService.markDisconnected(deviceId, session.getId(), buildDisconnectReason(status))) {
            realtimeEventPublisher.publish(new RealtimeEvent(
                    RealtimeEventType.DEVICE_DISCONNECTED,
                    deviceId,
                    Instant.now(),
                    buildDisconnectReason(status)
            ));
            LOGGER.info("Device disconnected: deviceId={}, sessionId={}, status={}", deviceId, session.getId(), status);
        } else {
            LOGGER.info("WebSocket connection closed: sessionId={}, status={}", session.getId(), status);
        }
    }

    // Maneja errores de transporte del socket.
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        LOGGER.error("Transport error on session {}", session.getId(), exception);
        if (session.isOpen()) {
            sendErrorAndClose(session, CloseStatus.SERVER_ERROR, "transport_error", "Transport error detected");
        }
    }

    // Convierte el texto entrante a JSON utilizable.
    private JsonNode parsePayload(String rawPayload) {
        try {
            return objectMapper.readTree(rawPayload);
        } catch (JsonProcessingException exception) {
            throw new InvalidDeviceMessageException("Invalid JSON payload");
        }
    }

    // Valida los campos minimos requeridos por cualquier mensaje.
    private void validateBaseMessage(BaseMessage baseMessage) {
        if (baseMessage == null || baseMessage.type() == null || baseMessage.type().isBlank()) {
            throw new InvalidDeviceMessageException("Message type is required");
        }
    }

    // Envia un error al cliente y luego cierra la sesion.
    private void sendErrorAndClose(WebSocketSession session,
                                   CloseStatus closeStatus,
                                   String errorCode,
                                   String errorMessage) throws IOException {
        String deviceId = resolveAuthenticatedDeviceId(session).orElse(null);
        try {
            webSocketJsonSender.sendError(session, errorCode, errorMessage, deviceId);
        } finally {
            if (session.isOpen()) {
                session.close(closeStatus);
            }
        }
    }

    // Obtiene el deviceId autenticado asociado a la sesion.
    private Optional<String> resolveAuthenticatedDeviceId(WebSocketSession session) {
        String deviceId = DeviceSessionAttributes.getAuthenticatedDeviceId(session);
        if (deviceId != null && !deviceId.isBlank()) {
            return Optional.of(deviceId);
        }
        return deviceSessionRegistry.findDeviceIdBySessionId(session.getId());
    }

    // Construye una razon legible de desconexion.
    private String buildDisconnectReason(CloseStatus status) {
        if (status == null) {
            return "Connection closed";
        }
        if (status.getReason() != null && !status.getReason().isBlank()) {
            return status.getReason();
        }
        return status.toString();
    }
}

