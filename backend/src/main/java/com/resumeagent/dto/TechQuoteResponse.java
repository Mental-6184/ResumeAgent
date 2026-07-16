package com.resumeagent.dto;

public record TechQuoteResponse(
        String quote,
        String author,
        String category
) {
}
