package com.neurolive.realtime.service;

import com.neurolive.realtime.model.RealtimeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// Registra en logs los eventos relevantes del servicio.
@Component
public class LoggingRealtimeEventObserver implements RealtimeEventObserver {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingRealtimeEventObserver.class);

    // Escribe un resumen legible del evento recibido.
    @Override
    public void onEvent(RealtimeEvent event) {
        LOGGER.info("Realtime event: type={}, deviceId={}, occurredAt={}", event.type(), event.deviceId(), event.occurredAt());
    }
}

