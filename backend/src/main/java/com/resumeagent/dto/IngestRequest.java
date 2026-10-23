package com.resumeagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IngestRequest(
        @NotBlank @Size(max = 40) String sourceType,
        @NotBlank @Size(max = 200) String sourceName,
        @NotBlank @Size(max = 20000) String content
) {
}

