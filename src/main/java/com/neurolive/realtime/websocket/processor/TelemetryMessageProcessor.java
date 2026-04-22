package com.neurolive.realtime.websocket.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neurolive.realtime.dto.TelemetryMessage;
import com.neurolive.realtime.service.TelemetryIngestionService;
import com.neurolive.realtime.websocket.MessageProcessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

// Procesa la telemetria biometrica recibida.
@Component
public class TelemetryMessageProcessor extends AbstractDeviceMessageProcessor implements MessageProcessor {

    private final TelemetryIngestionService telemetryIngestionService;

    // Recibe el servicio encargado de almacenar telemetria.
    public TelemetryMessageProcessor(ObjectMapper objectMapper,
                                     TelemetryIngestionService telemetryIngestionService) {
        super(objectMapper);
        this.telemetryIngestionService = telemetryIngestionService;
    }

    // Retorna el tipo soportado por este procesador.
    @Override
    public String supportedType() {
        return "telemetry";
    }

    // Valida la sesion autenticada y guarda la telemetria.
    @Override
    public void process(JsonNode payload, WebSocketSession session) {
        TelemetryMessage telemetryMessage = readMessage(payload, TelemetryMessage.class);
        requireAuthenticatedDevice(session, telemetryMessage.deviceId());
        telemetryIngestionService.storeTelemetry(telemetryMessage);
    }
}

