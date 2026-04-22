package com.neurolive.realtime.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// Representa la petición REST para controlar la luz.
public record LightCommandRequest(
        @NotBlank String color,
        @NotNull @Min(0) @Max(100) Integer intensity,
        @NotBlank String mode
) {
}

