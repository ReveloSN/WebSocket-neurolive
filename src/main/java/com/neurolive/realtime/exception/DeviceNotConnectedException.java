package com.neurolive.realtime.exception;

// Señala que no existe una sesión activa para el dispositivo.
public class DeviceNotConnectedException extends RuntimeException {

    // Crea la excepción con el dispositivo afectado.
    public DeviceNotConnectedException(String message) {
        super(message);
    }
}

