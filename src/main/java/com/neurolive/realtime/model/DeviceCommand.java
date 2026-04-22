package com.neurolive.realtime.model;

import com.neurolive.realtime.dto.DeviceCommandMessage;

// Modela un comando enviable al dispositivo.
public interface DeviceCommand {

    // Retorna el identificador del comando.
    String commandId();

    // Retorna el dispositivo destino.
    String deviceId();

    // Retorna la acción del comando.
    String action();

    // Retorna la carga útil del comando.
    Object payload();

    // Convierte el comando en mensaje transportable.
    DeviceCommandMessage toMessage();
}

