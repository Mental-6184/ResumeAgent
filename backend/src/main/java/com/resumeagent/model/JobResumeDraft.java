package com.resumeagent.model;

import java.util.List;

public record JobResumeDraft(
        String summary,
        List<String> skills,
        List<ProjectSuggestion> projectSuggestions,
        List<String> keywords,
        boolean edited,
        long updatedAt
) {
    public record ProjectSuggestion(String project, String original, String suggestion, String reason) {
    }
}
