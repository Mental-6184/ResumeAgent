package com.resumeagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeagent.config.ResumeAgentProperties;
import com.resumeagent.dto.ExportInterviewRequest;
import com.resumeagent.dto.ExportResumeRequest;
import com.resumeagent.dto.InterviewReviewRequest;
import com.resumeagent.dto.InterviewReviewRequest.ReviewMessage;
import com.resumeagent.dto.ProfileSaveRequest;
import com.resumeagent.model.ProfileForm;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 导出服务单测：Markdown 结构、PDF/降级行为、复盘导出、非法输入。
 */
class ExportServiceTest {

    @TempDir
    Path tempDir;

    private ProfileService profileService;
    private InterviewReviewService reviewService;

    @BeforeEach
    void setUp() {
        ResumeAgentProperties properties = new ResumeAgentProperties();
        KnowledgeBaseService kb = new KnowledgeBaseService(new EmbeddingService());
        profileService = new ProfileService(kb, new ObjectMapper(), tempDir);
        reviewService = new InterviewReviewService(new SafetyService(properties),
                new AuditService(properties), new LlmService(properties, new ObjectMapper()), tempDir.resolve("reviews"));

        ProfileForm form = new ProfileForm("张三", "", "", "", "", "", "", "", "Java 开发", "",
                "精通 Java", "分布式订单系统", "", "", "");
        profileService.activate(profileService.create(
                new ProfileSaveRequest("校招模板", form, "张三的简历全文", "zhangsan.pdf")).id());
    }

    @Test
    void resumeExportWithoutFontShouldFallBackToMarkdown() {
        // 空字体候选 → PDF 必然降级为 Markdown
        ExportService service = new ExportService(profileService, reviewService, List.of());

        var file = service.exportResume(new ExportResumeRequest("pdf", null, null));

        assertThat(file.format()).isEqualTo("md");
        String content = new String(file.content(), StandardCharsets.UTF_8);
        assertThat(content).contains("简历优化报告");
        assertThat(content).contains("张三");
        assertThat(content).contains("简历全文");
    }

    @Test
    void resumeExportShouldProduceValidPdfWhenFontAvailable() {
        ExportService service = new ExportService(profileService, reviewService);

        var file = service.exportResume(new ExportResumeRequest("pdf", null, null));

        if ("pdf".equals(file.format())) {
            assertThat(file.content()).startsWith("%PDF".getBytes(StandardCharsets.US_ASCII));
            assertThat(file.content().length).isGreaterThan(500);
        } else {
            // 无中文字体环境：必须降级为 md，而不是乱码 PDF
            assertThat(file.format()).isEqualTo("md");
        }
    }

    @Test
    void interviewExportShouldContainTranscriptAndReview() {
        var review = reviewService.review(new InterviewReviewRequest(List.of(
                new ReviewMessage("user", "介绍一个项目"),
                new ReviewMessage("assistant", "我做过分布式订单系统，QPS 提升 3 倍。")
        ), "Java 开发", "第一场"));
        ExportService service = new ExportService(profileService, reviewService, List.of());

        var file = service.exportInterview(new ExportInterviewRequest("md", review.id(), null,
                List.of(new ReviewMessage("user", "介绍一个项目"),
                        new ReviewMessage("assistant", "我做过分布式订单系统，QPS 提升 3 倍。"))));

        String content = new String(file.content(), StandardCharsets.UTF_8);
        assertThat(content).contains("模拟面试复盘");
        assertThat(content).contains("问答记录");
        assertThat(content).contains("复盘详情");
        assertThat(content).contains("改进方向");
    }

    @Test
    void exportWithoutTemplateOrReviewShouldThrow() {
        ExportService service = new ExportService(profileService, reviewService, List.of());
        ProfileService emptyProfile = new ProfileService(new KnowledgeBaseService(new EmbeddingService()),
                new ObjectMapper(), tempDir.resolve("empty"));

        assertThatThrownBy(() -> new ExportService(emptyProfile, reviewService, List.of())
                .exportResume(new ExportResumeRequest("md", null, null)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.exportInterview(new ExportInterviewRequest("md", null, "x", List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
