package com.neurolive.realtime.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

// Verifica las reglas de validacion del comando de luz.
class LightCommandRequestValidationTests {

    private static Validator validator;

    // Inicializa el validador para las pruebas.
    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    // Comprueba que una peticion valida no genera errores.
    @Test
    void shouldAcceptValidLightCommandRequest() {
        LightCommandRequest request = new LightCommandRequest("#4A90E2", 60, "calm");

        Set<ConstraintViolation<LightCommandRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    // Comprueba que se rechaza un color invalido.
    @Test
    void shouldRejectInvalidHexColor() {
        LightCommandRequest request = new LightCommandRequest("blue", 60, "calm");

        Set<ConstraintViolation<LightCommandRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .anyMatch(path -> path.toString().equals("color"));
    }

    // Comprueba que se rechaza un modo fuera del conjunto permitido.
    @Test
    void shouldRejectUnsupportedMode() {
        LightCommandRequest request = new LightCommandRequest("#4A90E2", 60, "blink");

        Set<ConstraintViolation<LightCommandRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .anyMatch(path -> path.toString().equals("mode"));
    }
}

