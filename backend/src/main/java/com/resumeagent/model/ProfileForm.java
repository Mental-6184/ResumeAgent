package com.resumeagent.model;

/**
 * 网申式结构化个人信息表单。
 * 全部字段为用户在前端填写的真实信息，是 AI 回答的第一事实来源。
 */
public record ProfileForm(
        String name,
        String phone,
        String email,
        String city,
        String school,
        String major,
        String degree,
        String graduationYear,
        String targetPosition,
        String expectedSalary,
        String skills,
        String projectExperience,
        String internshipExperience,
        String selfEvaluation,
        String awards
) {

    public static ProfileForm empty() {
        return new ProfileForm("", "", "", "", "", "", "", "", "", "", "", "", "", "", "");
    }

    /** 渲染为结构化纯文本，用于知识库存储和提示词注入 */
    public String toText() {
        StringBuilder builder = new StringBuilder();
        append(builder, "姓名", name);
        append(builder, "手机", phone);
        append(builder, "邮箱", email);
        append(builder, "城市", city);
        append(builder, "学校", school);
        append(builder, "专业", major);
        append(builder, "学历", degree);
        append(builder, "毕业年份", graduationYear);
        append(builder, "求职意向", targetPosition);
        append(builder, "期望薪资", expectedSalary);
        append(builder, "技能特长", skills);
        append(builder, "项目经历", projectExperience);
        append(builder, "实习经历", internshipExperience);
        append(builder, "自我评价", selfEvaluation);
        append(builder, "荣誉奖项", awards);
        return builder.toString().trim();
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isEmpty() {
        return toText().isBlank();
    }

    private void append(StringBuilder builder, String label, String value) {
        if (value != null && !value.isBlank()) {
            builder.append(label).append("：").append(value.trim()).append('\n');
        }
    }
}
