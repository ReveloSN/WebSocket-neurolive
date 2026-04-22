package com.neurolive.realtime.websocket.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neurolive.realtime.dto.HeartbeatMessage;
import com.neurolive.realtime.service.HeartbeatMonitorService;
import com.neurolive.realtime.websocket.MessageProcessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

// Procesa heartbeats enviados por los dispositivos.
@Component
public class HeartbeatMessageProcessor extends AbstractDeviceMessageProcessor implements MessageProcessor {

    private final HeartbeatMonitorService heartbeatMonitorService;

    // Recibe el servicio encargado del heartbeat.
    public HeartbeatMessageProcessor(ObjectMapper objectMapper,
                                     HeartbeatMonitorService heartbeatMonitorService) {
        super(objectMapper);
        this.heartbeatMonitorService = heartbeatMonitorService;
    }

    // Retorna el tipo soportado por este procesador.
    @Override
    public String supportedType() {
        return "heartbeat";
    }

    // Valida la sesion autenticada y registra el heartbeat.
    @Override
    public void process(JsonNode payload, WebSocketSession session) {
        HeartbeatMessage heartbeatMessage = readMessage(payload, HeartbeatMessage.class);
        String deviceId = requireAuthenticatedDevice(session, heartbeatMessage.deviceId());
        heartbeatMonitorService.recordHeartbeat(deviceId);
    }
}

