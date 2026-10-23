package com.resumeagent.dto;

/** 知识库文档条目（builtin = 内置只读文档，manual = 用户上传） */
public record KnowledgeDocumentInfo(
        String sourceName,
        String sourceType,
        int chunks,
        long uploadedAt,
        boolean builtin
) {
}
