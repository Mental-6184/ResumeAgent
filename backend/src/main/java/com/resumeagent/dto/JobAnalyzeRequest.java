package com.resumeagent.dto;

import jakarta.validation.constraints.NotBlank;

public record JobAnalyzeRequest(
        @NotBlank String jobTitle,
        @NotBlank String jobDescription
) {
}

