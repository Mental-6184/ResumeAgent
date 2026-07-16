package com.resumeagent.dto;

import java.util.List;

public record KnowledgeSearchResponse(
        List<KnowledgeSearchItem> items
) {
}

