package com.resumeagent.dto;

import java.util.List;

public record ProjectQueryResponse(
        String query,
        List<ProjectInfo> projects,
        String summary
) {
    public record ProjectInfo(
            String name,
            String description,
            List<String> techStack,
            String role,
            List<String> highlights
    ) {
    }
}
