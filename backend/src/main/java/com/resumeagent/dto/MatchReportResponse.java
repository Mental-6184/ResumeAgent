package com.resumeagent.dto;

import java.util.List;

/**
 * 简历/JD 深度匹配报告：总分 + 维度分 + 逐条 JD 要求匹配 + 简历修改建议。
 * degraded=true 表示 LLM 未配置、由关键词降级分析生成。
 */
public record MatchReportResponse(
        String jobTitle,
        int overallScore,
        List<DimensionScore> dimensions,
        List<RequirementMatch> requirementMatches,
        List<ResumeSuggestion> resumeSuggestions,
        String summary,
        boolean degraded
) {

    /** 单个分析维度（0-100 + 点评） */
    public record DimensionScore(String name, int score, String comment) {
    }

    /** 逐条 JD 要求的匹配情况 */
    public record RequirementMatch(String requirement, String status, String evidence, String advice) {
    }

    /** 简历修改建议（原文片段 → 改写建议 → 理由） */
    public record ResumeSuggestion(String original, String suggestion, String reason) {
    }
}
