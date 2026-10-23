package com.resumeagent.dto;

/** 复盘存档列表条目（用于多场面试对比） */
public record InterviewReviewSummary(
        String id,
        String title,
        long createdAt,
        int overallScore
) {
}
