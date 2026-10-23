package com.resumeagent.model;

import com.resumeagent.dto.MatchReportResponse;
import java.util.List;

public record JobRecord(
        String id,
        String company,
        String title,
        String location,
        String salaryRange,
        String url,
        String jobDescription,
        Long deadline,
        JobStatus status,
        String notes,
        String profileTemplateId,
        MatchReportResponse matchReport,
        JobResumeDraft resumeDraft,
        List<String> reviewIds,
        long createdAt,
        long updatedAt
) {
    public JobRecord {
        reviewIds = reviewIds == null ? List.of() : List.copyOf(reviewIds);
        status = status == null ? JobStatus.SAVED : status;
    }
}
