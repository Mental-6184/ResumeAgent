package com.resumeagent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeagent.config.ResumeAgentProperties;
import com.resumeagent.dto.ChatRequest;
import com.resumeagent.dto.ProfileSaveRequest;
import com.resumeagent.model.ProfileForm;
import com.resumeagent.service.AuditService;
import com.resumeagent.service.ChatService;
import com.resumeagent.service.EmbeddingService;
import com.resumeagent.service.KnowledgeBaseService;
import com.resumeagent.service.LlmService;
import com.resumeagent.service.PromptBuilder;
import com.resumeagent.service.ProfileService;
import com.resumeagent.service.SafetyService;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AI 工作流验收：激活用户档案后，回答必须以用户亲手填写/上传的信息为事实来源。
 * LLM 未配置（走降级回答），断言不依赖网络。
 */
class ProfileGroundingTest {

    @TempDir
    Path tempDir;

    @Test
    void chatAnswersShouldGroundInUserProfile() {
        ResumeAgentProperties properties = new ResumeAgentProperties();
        KnowledgeBaseService kb = new KnowledgeBaseService(new EmbeddingService());
        ProfileService profileService = new ProfileService(kb, new ObjectMapper(), tempDir);
        ChatService chatService = new ChatService(kb, new SafetyService(properties), new PromptBuilder(),
                new LlmService(properties, new ObjectMapper()), new AuditService(properties), profileService);

        ProfileForm form = new ProfileForm("张三", "13800000000", "zhangsan@example.com", "深圳",
                "南山理工大学", "人工智能", "硕士", "2027",
                "AI 应用开发", "30k", "熟练掌握 Java 与 Spring AI",
                "独立开发了智能简历助手", "在字节跳动 AI Lab 实习半年", "热爱开源", "国家奖学金");
        profileService.activate(profileService.create(
                new ProfileSaveRequest("校招模板", form, "张三的简历：智能简历助手项目主导者", "zhangsan.pdf")).id());

        var response = chatService.chat(new ChatRequest("conv-1", "介绍一下你自己", "rag"));

        // 引用中必须出现用户档案
        assertThat(response.citations()).anyMatch(citation -> citation.contains("我的档案"));
        // 降级回答围绕用户输入（含用户真实信息），而不是固定话术
        assertThat(response.reply()).contains("张三");
        assertThat(response.reply()).doesNotContain("上传简历");
    }

    @Test
    void switchingProfileSwitchesFacts() {
        ResumeAgentProperties properties = new ResumeAgentProperties();
        KnowledgeBaseService kb = new KnowledgeBaseService(new EmbeddingService());
        ProfileService profileService = new ProfileService(kb, new ObjectMapper(), tempDir);
        ChatService chatService = new ChatService(kb, new SafetyService(properties), new PromptBuilder(),
                new LlmService(properties, new ObjectMapper()), new AuditService(properties), profileService);

        ProfileForm first = new ProfileForm("张三", "", "", "", "", "", "", "", "", "Java 开发",
                "", "", "", "", "");
        ProfileForm second = new ProfileForm("李四", "", "", "", "", "", "", "", "", "前端开发",
                "", "", "", "", "");
        var firstId = profileService.create(new ProfileSaveRequest("甲", first, "张三简历", "")).id();
        var secondId = profileService.create(new ProfileSaveRequest("乙", second, "李四简历", "")).id();

        profileService.activate(firstId);
        var before = chatService.chat(new ChatRequest("conv-2", "你是谁", "rag"));
        assertThat(before.reply()).contains("张三");

        profileService.activate(secondId);
        var after = chatService.chat(new ChatRequest("conv-3", "你是谁", "rag"));
        assertThat(after.reply()).contains("李四");
        assertThat(after.reply()).doesNotContain("张三");
    }

    @Test
    void systemPromptShouldPutUserProfileFirstWithConflictRule() {
        PromptBuilder promptBuilder = new PromptBuilder();
        List<com.resumeagent.dto.KnowledgeSearchItem> contexts = List.of(
                new com.resumeagent.dto.KnowledgeSearchItem("我的档案", "resume", "姓名：王小明\n学校：华东科技大学", 1.0),
                new com.resumeagent.dto.KnowledgeSearchItem("个人简历", "resume", "我叫崔哲航，中北大学学生", 0.9));

        String prompt = promptBuilder.buildDynamicSystemPrompt(contexts, 0);

        // 用户档案独立成节且声明最高优先级
        assertThat(prompt).contains("用户档案（最高优先级");
        assertThat(prompt).contains("冲突时一律以本节为准");
        // 档案内容排在其它资料之前
        assertThat(prompt.indexOf("王小明")).isLessThan(prompt.indexOf("崔哲航"));
        // 明确冲突裁决：忽略其它资料中的冲突身份
        assertThat(prompt).contains("忽略其它资料中的冲突部分");
    }

    @Test
    void switchingProfileMidConversationDropsStaleIdentityHistory() {
        ResumeAgentProperties properties = new ResumeAgentProperties();
        KnowledgeBaseService kb = new KnowledgeBaseService(new EmbeddingService());
        ProfileService profileService = new ProfileService(kb, new ObjectMapper(), tempDir);
        ChatService chatService = new ChatService(kb, new SafetyService(properties), new PromptBuilder(),
                new LlmService(properties, new ObjectMapper()), new AuditService(properties), profileService);

        ProfileForm first = new ProfileForm("张三", "", "", "", "", "", "", "", "", "Java 开发",
                "", "", "", "", "");
        ProfileForm second = new ProfileForm("李四", "", "", "", "", "", "", "", "", "前端开发",
                "", "", "", "", "");
        var firstId = profileService.create(new ProfileSaveRequest("甲", first, "张三简历", "")).id();
        var secondId = profileService.create(new ProfileSaveRequest("乙", second, "李四简历", "")).id();

        // 同一个对话：先以张三身份聊一轮，历史里留下"张三"身份
        profileService.activate(firstId);
        var before = chatService.chat(new ChatRequest("conv-same", "你是谁", "rag"));
        assertThat(before.reply()).contains("张三");

        // 切换档案后继续同一对话：旧历史必须作废，回答跟随新档案
        profileService.activate(secondId);
        var after = chatService.chat(new ChatRequest("conv-same", "你是谁", "rag"));
        assertThat(after.reply()).contains("李四");
        assertThat(after.reply()).doesNotContain("张三");
    }

    @Test
    void greetingWithoutProfileShouldBeSafeAndNeverFabricate() {
        ResumeAgentProperties properties = new ResumeAgentProperties();
        KnowledgeBaseService kb = new KnowledgeBaseService(new EmbeddingService());
        ChatService chatService = new ChatService(kb, new SafetyService(properties), new PromptBuilder(),
                new LlmService(properties, new ObjectMapper()), new AuditService(properties),
                new ProfileService(kb, new ObjectMapper(), tempDir));

        var response = chatService.chat(new ChatRequest("conv-greet", "你好", "rag"));

        // 无档案的问候：确定性简短问候，绝不编造姓名/学校/占位符
        assertThat(response.reply()).isEqualTo(new PromptBuilder().buildGreetingAnswer());
        assertThat(response.reply()).doesNotContain("姓名").doesNotContain("大学").doesNotContain("[");
    }

    @Test
    void promptWithoutProfileShouldForbidPlaceholders() {
        PromptBuilder promptBuilder = new PromptBuilder();
        List<com.resumeagent.dto.KnowledgeSearchItem> contexts = List.of(
                new com.resumeagent.dto.KnowledgeSearchItem("ResumeAgent", "project", "基于 RAG 的问答系统", 0.9));

        String prompt = promptBuilder.buildDynamicSystemPrompt(contexts, 0);

        // 没有用户档案时：显式禁止占位符编造
        assertThat(prompt).contains("没有用户档案");
        assertThat(prompt).contains("禁止使用占位符");
    }

    @Test
    void emptyProfileFallbackShouldNotGiveCannedOnboardingSpeech() {
        ResumeAgentProperties properties = new ResumeAgentProperties();
        KnowledgeBaseService kb = new KnowledgeBaseService(new EmbeddingService());
        ChatService chatService = new ChatService(kb, new SafetyService(properties), new PromptBuilder(),
                new LlmService(properties, new ObjectMapper()), new AuditService(properties),
                new ProfileService(kb, new ObjectMapper(), tempDir));

        var response = chatService.chat(new ChatRequest("conv-4", "谈谈你的开源经历", "rag"));

        assertThat(response.reply()).isNotBlank();
        assertThat(response.reply()).doesNotContain("知识库");
        assertThat(response.reply()).doesNotContain("上传简历");
    }
}
