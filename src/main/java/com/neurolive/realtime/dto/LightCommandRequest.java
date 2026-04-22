package com.neurolive.realtime.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// Representa la peticion REST para controlar la luz.
public record LightCommandRequest(
        @NotBlank
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "color must use the format #RRGGBB")
        String color,
        @NotNull @Min(0) @Max(100) Integer intensity,
        @NotBlank
        @Pattern(regexp = "^(calm|steady|pulse)$", message = "mode must be one of: calm, steady, pulse")
        String mode
) {
}

