package com.resumeagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 保存知识库文档（粘贴文本）请求 */
public record KnowledgeSaveRequest(
        @NotBlank(message = "文档名称不能为空") @Size(max = 200) String sourceName,
        @Size(max = 40) String sourceType,
        @NotBlank(message = "文档内容不能为空") @Size(max = 20000) String content
) {
}
