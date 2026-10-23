package com.resumeagent.dto;

import com.resumeagent.model.JobStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JobSaveRequest(
        @NotBlank @Size(max = 100) String company,
        @NotBlank @Size(max = 100) String title,
        @Size(max = 100) String location,
        @Size(max = 100) String salaryRange,
        @Size(max = 500) String url,
        @NotBlank @Size(max = 30000) String jobDescription,
        Long deadline,
        JobStatus status,
        @Size(max = 5000) String notes
) {
}
