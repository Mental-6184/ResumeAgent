package com.resumeagent.dto;

import jakarta.validation.constraints.NotBlank;

public record JobReviewLinkRequest(@NotBlank String reviewId) {
}
