package com.resumeagent.dto;

public record KnowledgeSearchItem(
        String sourceName,
        String sourceType,
        String content,
        double score
) {
}

