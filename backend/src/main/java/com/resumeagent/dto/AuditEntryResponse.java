package com.resumeagent.dto;

public record AuditEntryResponse(
        String timestamp,
        String category,
        String conversationId,
        String input,
        String output,
        String notes
) {
}

