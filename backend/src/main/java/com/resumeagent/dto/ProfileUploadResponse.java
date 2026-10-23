package com.resumeagent.dto;

/** 简历文件上传解析响应（仅解析预览，不落库；确认后通过 /templates 保存） */
public record ProfileUploadResponse(
        String fileName,
        String extractedText,
        int charCount
) {
}
