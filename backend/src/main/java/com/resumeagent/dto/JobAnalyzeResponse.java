package com.resumeagent.dto;

import java.util.List;

public record JobAnalyzeResponse(
        String jobTitle,
        int matchScore,
        List<String> strengths,
        List<String> gaps,
        List<String> suggestions
) {
}

