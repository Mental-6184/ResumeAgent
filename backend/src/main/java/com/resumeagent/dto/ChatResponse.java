package com.resumeagent.dto;

import java.util.List;

public record ChatResponse(
        String conversationId,
        String reply,
        List<String> citations
) {
}

