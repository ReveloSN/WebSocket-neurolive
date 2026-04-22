package com.neurolive.realtime.exception;

import com.neurolive.realtime.dto.ApiErrorResponse;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Traduce excepciones a respuestas REST consistentes.
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Maneja errores de dispositivo no conectado.
    @ExceptionHandler(DeviceNotConnectedException.class)
    public ResponseEntity<ApiErrorResponse> handleDeviceNotConnected(DeviceNotConnectedException exception) {
        return buildResponse(HttpStatus.CONFLICT, exception.getMessage());
    }

    // Maneja errores de autenticación básica.
    @ExceptionHandler(DeviceAuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(DeviceAuthenticationException exception) {
        return buildResponse(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    // Maneja errores de entrada inválida.
    @ExceptionHandler({InvalidDeviceMessageException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequest(RuntimeException exception) {
        return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    // Maneja errores de validación de DTOs.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Request validation failed");
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    // Maneja fallos inesperados del servicio.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected internal error");
    }

    // Construye una respuesta estándar de error.
    private ResponseEntity<ApiErrorResponse> buildResponse(HttpStatus status, String message) {
        ApiErrorResponse body = new ApiErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message);
        return ResponseEntity.status(status).body(body);
    }
}

