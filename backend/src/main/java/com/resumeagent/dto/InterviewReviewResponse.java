package com.resumeagent.dto;

import java.util.List;

/**
 * 模拟面试复盘报告：总分 + 维度 + 亮点/改进 + 逐题点评，支持存档对比。
 * degraded=true 表示 LLM 未配置、由统计降级生成。
 */
public record InterviewReviewResponse(
        String id,
        String title,
        long createdAt,
        int overallScore,
        List<ReviewDimension> dimensions,
        List<String> highlights,
        List<String> improvements,
        List<QuestionReview> perQuestion,
        String summary,
        boolean degraded
) {

    public record ReviewDimension(String name, int score, String comment) {
    }

    public record QuestionReview(String question, int score, String comment, String improvement) {
    }
}
