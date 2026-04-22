package com.neurolive.realtime.exception;

// Señala que la autenticación del dispositivo falló.
public class DeviceAuthenticationException extends RuntimeException {

    // Crea la excepción con el detalle del fallo.
    public DeviceAuthenticationException(String message) {
        super(message);
    }
}

