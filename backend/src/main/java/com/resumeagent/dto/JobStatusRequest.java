package com.resumeagent.dto;

import com.resumeagent.model.JobStatus;
import jakarta.validation.constraints.NotNull;

public record JobStatusRequest(@NotNull JobStatus status) {
}
