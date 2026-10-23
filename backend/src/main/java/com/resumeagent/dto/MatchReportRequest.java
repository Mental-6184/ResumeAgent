package com.resumeagent.dto;

import jakarta.validation.constraints.NotBlank;

/** 简历/JD 深度匹配报告请求 */
public record MatchReportRequest(
        @NotBlank(message = "岗位名称不能为空") String jobTitle,
        @NotBlank(message = "岗位描述不能为空") String jobDescription
) {
}
