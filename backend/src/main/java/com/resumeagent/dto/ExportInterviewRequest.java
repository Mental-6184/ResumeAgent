package com.resumeagent.dto;

import java.util.List;

/** 面试导出请求（pdf|md；reviewId 为已存档复盘；messages 可选，附带问答原文） */
public record ExportInterviewRequest(
        String format,
        String reviewId,
        String title,
        List<InterviewReviewRequest.ReviewMessage> messages
) {
}
