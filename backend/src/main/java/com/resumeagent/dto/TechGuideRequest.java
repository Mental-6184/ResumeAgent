package com.resumeagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TechGuideRequest(
        @NotBlank @Size(max = 40) String module,
        @Size(max = 100) String tag,
        @NotBlank @Size(max = 4000) String message,
        @NotBlank @Size(max = 100) String conversationId
) {
}
