package com.resumeagent.model;

public record SafetyVerdict(
        boolean allowed,
        boolean injectionDetected,
        boolean sensitiveDetected,
        String sanitizedText,
        String reason
) {
}

