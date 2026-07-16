package com.resumeagent.dto;

import java.util.List;

public record InterviewAnswerResponse(
        String question,
        String answer,
        List<String> keyPoints,
        List<String> tips
) {
}
