package com.resumeagent.dto;

public record IngestResponse(
        boolean success,
        String message,
        int chunks
) {
}

