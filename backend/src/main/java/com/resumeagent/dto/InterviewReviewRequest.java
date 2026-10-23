package com.resumeagent.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** 模拟面试复盘请求：一场面试的完整消息记录 */
public record InterviewReviewRequest(
        @NotEmpty(message = "面试记录不能为空") List<ReviewMessage> messages,
        String jobTitle,
        String sessionTitle
) {

    public record ReviewMessage(String role, String content) {
    }
}
