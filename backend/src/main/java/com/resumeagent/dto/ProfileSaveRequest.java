package com.resumeagent.dto;

import com.resumeagent.model.ProfileForm;
import jakarta.validation.constraints.NotBlank;

/** 保存（新建/更新）档案模板请求 */
public record ProfileSaveRequest(
        @NotBlank(message = "模板名称不能为空") String name,
        ProfileForm form,
        String resumeText,
        String resumeFileName
) {
}
