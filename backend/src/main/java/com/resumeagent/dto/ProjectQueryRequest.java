package com.resumeagent.dto;

import jakarta.validation.constraints.NotBlank;

public record ProjectQueryRequest(
        @NotBlank String query,
        String techStack,
        String timeRange
) {
}
