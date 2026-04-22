package com.neurolive.realtime.exception;

// Señala que el mensaje WebSocket no cumple el contrato esperado.
public class InvalidDeviceMessageException extends RuntimeException {

    // Crea la excepción con un mensaje legible.
    public InvalidDeviceMessageException(String message) {
        super(message);
    }
}

