package com.neurolive.realtime.websocket.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neurolive.realtime.dto.AckMessage;
import com.neurolive.realtime.model.RealtimeEvent;
import com.neurolive.realtime.model.RealtimeEventType;
import com.neurolive.realtime.service.RealtimeEventPublisher;
import com.neurolive.realtime.websocket.MessageProcessor;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

// Procesa acuses enviados por el dispositivo.
@Component
public class AckMessageProcessor extends AbstractDeviceMessageProcessor implements MessageProcessor {

    private final RealtimeEventPublisher realtimeEventPublisher;

    // Recibe el publicador de eventos internos.
    public AckMessageProcessor(ObjectMapper objectMapper,
                               RealtimeEventPublisher realtimeEventPublisher) {
        super(objectMapper);
        this.realtimeEventPublisher = realtimeEventPublisher;
    }

    // Retorna el tipo soportado por este procesador.
    @Override
    public String supportedType() {
        return "ack";
    }

    // Valida el acuse y publica el evento correspondiente.
    @Override
    public void process(JsonNode payload, WebSocketSession session) {
        AckMessage ackMessage = readMessage(payload, AckMessage.class);
        requireAuthenticatedDevice(session, ackMessage.deviceId());
        realtimeEventPublisher.publish(new RealtimeEvent(
                RealtimeEventType.COMMAND_ACKNOWLEDGED,
                ackMessage.deviceId(),
                Instant.now(),
                ackMessage
        ));
    }
}

