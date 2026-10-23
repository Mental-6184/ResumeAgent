package com.resumeagent.model;

/**
 * 用户档案模板：网申表单 + 简历文本，可保存多份并在其间切换。
 * 激活的模板是 AI 回答的第一事实来源。
 */
public record ProfileTemplate(
        String id,
        String name,
        ProfileForm form,
        String resumeText,
        String resumeFileName,
        long createdAt,
        long updatedAt
) {

    /** 合成知识库文本：表单信息 + 简历全文 */
    public String toKnowledgeText() {
        StringBuilder builder = new StringBuilder();
        builder.append("【用户档案】以下是本人亲自填写/上传的真实信息，回答必须以此为准。\n\n");
        String formText = form == null ? "" : form.toText();
        if (!formText.isBlank()) {
            builder.append("## 基本信息（网申填写）\n").append(formText).append('\n');
        }
        if (resumeText != null && !resumeText.isBlank()) {
            builder.append("## 简历全文");
            if (resumeFileName != null && !resumeFileName.isBlank()) {
                builder.append("（来源文件：").append(resumeFileName).append('）');
            }
            builder.append('\n').append(resumeText.trim()).append('\n');
        }
        return builder.toString();
    }

    /** 档案是否包含任何实质内容 */
    public boolean hasContent() {
        boolean formEmpty = form == null || form.isEmpty();
        boolean resumeEmpty = resumeText == null || resumeText.isBlank();
        return !formEmpty || !resumeEmpty;
    }
}
