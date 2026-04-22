package com.neurolive.realtime.websocket.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neurolive.realtime.dto.AuthMessage;
import com.neurolive.realtime.dto.AuthResultMessage;
import com.neurolive.realtime.exception.InvalidDeviceMessageException;
import com.neurolive.realtime.model.RealtimeEvent;
import com.neurolive.realtime.model.RealtimeEventType;
import com.neurolive.realtime.service.ConnectionStatusService;
import com.neurolive.realtime.service.DeviceAuthenticationService;
import com.neurolive.realtime.service.DeviceSessionRegistry;
import com.neurolive.realtime.service.RealtimeEventPublisher;
import com.neurolive.realtime.websocket.DeviceSessionAttributes;
import com.neurolive.realtime.websocket.MessageProcessor;
import com.neurolive.realtime.websocket.WebSocketJsonSender;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

// Procesa la autenticacion inicial de un dispositivo.
@Component
public class AuthMessageProcessor extends AbstractDeviceMessageProcessor implements MessageProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthMessageProcessor.class);

    private final DeviceAuthenticationService deviceAuthenticationService;
    private final DeviceSessionRegistry deviceSessionRegistry;
    private final ConnectionStatusService connectionStatusService;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final WebSocketJsonSender webSocketJsonSender;

    // Recibe las dependencias necesarias para autenticar.
    public AuthMessageProcessor(ObjectMapper objectMapper,
                                DeviceAuthenticationService deviceAuthenticationService,
                                DeviceSessionRegistry deviceSessionRegistry,
                                ConnectionStatusService connectionStatusService,
                                RealtimeEventPublisher realtimeEventPublisher,
                                WebSocketJsonSender webSocketJsonSender) {
        super(objectMapper);
        this.deviceAuthenticationService = deviceAuthenticationService;
        this.deviceSessionRegistry = deviceSessionRegistry;
        this.connectionStatusService = connectionStatusService;
        this.realtimeEventPublisher = realtimeEventPublisher;
        this.webSocketJsonSender = webSocketJsonSender;
    }

    // Retorna el tipo soportado por este procesador.
    @Override
    public String supportedType() {
        return "auth";
    }

    // Valida credenciales y registra la sesion activa.
    @Override
    public void process(JsonNode payload, WebSocketSession session) {
        AuthMessage authMessage = readMessage(payload, AuthMessage.class);
        validateAuthMessage(authMessage, session);
        deviceAuthenticationService.authenticateOrThrow(authMessage);
        DeviceSessionAttributes.setAuthenticatedDeviceId(session, authMessage.deviceId());
        Optional<WebSocketSession> previousSession = deviceSessionRegistry.registerSession(authMessage.deviceId(), session);
        connectionStatusService.markAuthenticated(authMessage.deviceId(), session.getId());
        realtimeEventPublisher.publish(new RealtimeEvent(
                RealtimeEventType.DEVICE_AUTHENTICATED,
                authMessage.deviceId(),
                Instant.now(),
                session.getId()
        ));
        sendAuthResult(session, authMessage.deviceId());
        previousSession.ifPresent(this::closePreviousSession);
        LOGGER.info("Device authenticated successfully: deviceId={}, sessionId={}", authMessage.deviceId(), session.getId());
    }

    // Verifica que el mensaje auth sea coherente con la sesion.
    private void validateAuthMessage(AuthMessage authMessage, WebSocketSession session) {
        if (authMessage.deviceId() == null || authMessage.deviceId().isBlank()) {
            throw new InvalidDeviceMessageException("Auth deviceId is required");
        }
        String authenticatedDeviceId = DeviceSessionAttributes.getAuthenticatedDeviceId(session);
        if (authenticatedDeviceId != null && !authenticatedDeviceId.equals(authMessage.deviceId())) {
            throw new InvalidDeviceMessageException("Session is already authenticated with another deviceId");
        }
    }

    // Envia la confirmacion de autenticacion al ESP32.
    private void sendAuthResult(WebSocketSession session, String deviceId) {
        try {
            webSocketJsonSender.sendMessage(session, new AuthResultMessage(
                    "auth_result",
                    deviceId,
                    true,
                    "Authentication successful"
            ));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to send authentication result", exception);
        }
    }

    // Cierra una sesion anterior cuando el mismo deviceId se reconecta.
    private void closePreviousSession(WebSocketSession previousSession) {
        if (!previousSession.isOpen()) {
            return;
        }
        try {
            previousSession.close(CloseStatus.NORMAL.withReason("Replaced by a newer session"));
        } catch (IOException exception) {
            LOGGER.warn("Failed to close previous session {}", previousSession.getId(), exception);
        }
    }
}

