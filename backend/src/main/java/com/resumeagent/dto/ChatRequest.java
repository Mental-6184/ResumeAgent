package com.resumeagent.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @NotBlank String conversationId,
        @NotBlank String message,
        String mode
) {
    public ChatRequest {
        if (mode == null || mode.isBlank()) {
            mode = "rag";
        }
    }
}

