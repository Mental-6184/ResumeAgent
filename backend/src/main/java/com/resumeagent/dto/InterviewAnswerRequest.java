package com.resumeagent.dto;

import jakarta.validation.constraints.NotBlank;

public record InterviewAnswerRequest(
        @NotBlank String question,
        String jobTitle
) {
}
