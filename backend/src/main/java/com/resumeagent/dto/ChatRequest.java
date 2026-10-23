package com.resumeagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank @Size(max = 100) String conversationId,
        @NotBlank @Size(max = 4000) String message,
        @Size(max = 20) String mode,
        @Size(max = 100) String jobTitle
) {
    public ChatRequest(String conversationId, String message, String mode) {
        this(conversationId, message, mode, null);
    }

    public ChatRequest {
        if (mode == null || mode.isBlank()) {
            mode = "rag";
        }
    }
}

