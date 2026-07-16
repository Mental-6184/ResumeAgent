package com.resumeagent.dto;

import jakarta.validation.constraints.NotBlank;

public record IngestRequest(
        @NotBlank String sourceType,
        @NotBlank String sourceName,
        @NotBlank String content
) {
}

