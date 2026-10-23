package com.resumeagent.dto;

import com.resumeagent.model.ProfileForm;

/** 档案模板响应 */
public record ProfileTemplateResponse(
        String id,
        String name,
        ProfileForm form,
        String resumeText,
        String resumeFileName,
        long createdAt,
        long updatedAt,
        boolean active
) {
}
