package com.neurolive.realtime.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.neurolive.realtime.config.RealtimeProperties;
import com.neurolive.realtime.dto.TelemetryMessage;
import com.neurolive.realtime.exception.InvalidDeviceMessageException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Verifica las reglas de validacion de telemetria.
class TelemetryIngestionServiceTests {

    private TelemetryIngestionService telemetryIngestionService;

    // Prepara el servicio con dependencias simples para probar.
    @BeforeEach
    void setUp() {
        RealtimeProperties realtimeProperties = new RealtimeProperties();
        ConnectionStatusService connectionStatusService = new ConnectionStatusService();
        RealtimeEventPublisher realtimeEventPublisher = new RealtimeEventPublisher(List.of());
        telemetryIngestionService = new TelemetryIngestionService(
                realtimeProperties,
                connectionStatusService,
                realtimeEventPublisher
        );
    }

    // Comprueba que se requiere un timestamp valido.
    @Test
    void shouldRejectTelemetryWithoutTimestamp() {
        TelemetryMessage telemetryMessage = new TelemetryMessage(
                "telemetry",
                "ESP32_001",
                null,
                92,
                98,
                true
        );

        assertThatThrownBy(() -> telemetryIngestionService.storeTelemetry(telemetryMessage))
                .isInstanceOf(InvalidDeviceMessageException.class)
                .hasMessageContaining("timestamp");
    }

    // Comprueba que se rechaza un bpm fuera del rango esperado.
    @Test
    void shouldRejectTelemetryWithOutOfRangeBpm() {
        TelemetryMessage telemetryMessage = new TelemetryMessage(
                "telemetry",
                "ESP32_001",
                1710000000L,
                5,
                98,
                true
        );

        assertThatThrownBy(() -> telemetryIngestionService.storeTelemetry(telemetryMessage))
                .isInstanceOf(InvalidDeviceMessageException.class)
                .hasMessageContaining("bpm");
    }

    // Comprueba que se rechaza un spo2 fuera del rango esperado.
    @Test
    void shouldRejectTelemetryWithOutOfRangeSpo2() {
        TelemetryMessage telemetryMessage = new TelemetryMessage(
                "telemetry",
                "ESP32_001",
                1710000000L,
                92,
                30,
                true
        );

        assertThatThrownBy(() -> telemetryIngestionService.storeTelemetry(telemetryMessage))
                .isInstanceOf(InvalidDeviceMessageException.class)
                .hasMessageContaining("spo2");
    }
}
