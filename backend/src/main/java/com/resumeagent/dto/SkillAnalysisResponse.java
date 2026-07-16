package com.resumeagent.dto;

import java.util.List;

public record SkillAnalysisResponse(
        List<String> coreSkills,
        List<String> technicalStrengths,
        List<String> coverageAreas,
        List<String> improvementSuggestions,
        String summary
) {
}
