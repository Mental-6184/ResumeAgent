package com.resumeagent.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatRequestValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void conversationIdIsRequired() {
        var violations = validator.validate(new ChatRequest("", "hello", null));

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .contains("conversationId");
    }

    @Test
    void modeDefaultsToRag() {
        var request = new ChatRequest("session-1", "hello", null);

        assertThat(request.mode()).isEqualTo("rag");
    }
}
