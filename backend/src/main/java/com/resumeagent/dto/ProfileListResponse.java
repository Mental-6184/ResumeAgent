package com.resumeagent.dto;

import java.util.List;

/** 档案模板列表响应（含当前激活模板 id） */
public record ProfileListResponse(
        List<ProfileTemplateResponse> templates,
        String activeId
) {
}
