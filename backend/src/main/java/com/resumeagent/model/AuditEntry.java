package com.resumeagent.model;

import java.time.Instant;

public record AuditEntry(
        Instant timestamp,
        String category,
        String conversationId,
        String input,
        String output,
        String notes
) {
}

