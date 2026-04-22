package com.neurolive.realtime.model;

import com.neurolive.realtime.dto.DeviceCommandMessage;

// Representa un comando concreto de control lumínico.
public record LightCommand(String deviceId, String commandId, LightCommandPayload payload) implements DeviceCommand {

    // Retorna la acción que ejecutará el ESP32.
    @Override
    public String action() {
        return "set_light";
    }

    // Convierte el comando a un mensaje WebSocket.
    @Override
    public DeviceCommandMessage toMessage() {
        return new DeviceCommandMessage("command", commandId, action(), payload);
    }
}

