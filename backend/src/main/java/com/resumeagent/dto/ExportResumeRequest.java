package com.resumeagent.dto;

import com.resumeagent.model.JobResumeDraft;

/** 简历导出请求（pdf|md；templateId 为空时用激活模板；matchReport 可选，附带修改建议） */
public record ExportResumeRequest(
        String format,
        String templateId,
        MatchReportResponse matchReport,
        JobResumeDraft resumeDraft
) {
    public ExportResumeRequest(String format, String templateId, MatchReportResponse matchReport) {
        this(format, templateId, matchReport, null);
    }
}
