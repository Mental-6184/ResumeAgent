package com.resumeagent.service;

import com.resumeagent.dto.ExportInterviewRequest;
import com.resumeagent.dto.ExportResumeRequest;
import com.resumeagent.dto.InterviewReviewRequest;
import com.resumeagent.dto.InterviewReviewResponse;
import com.resumeagent.dto.MatchReportResponse;
import com.resumeagent.model.ProfileForm;
import com.resumeagent.model.ProfileTemplate;
import com.resumeagent.model.JobResumeDraft;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 导出服务：简历优化版 / 面试问答集与复盘，输出 PDF 或 Markdown。
 *
 * PDF 中文字体：按候选路径探测系统 CJK 字体（微软雅黑/宋体/Noto/文泉驿），
 * 找不到时自动降级为 Markdown —— 绝不输出乱码 PDF。
 */
@Service
public class ExportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExportService.class);
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float MARGIN = 50f;

    private static final List<String> FONT_CANDIDATES = List.of(
            "C:/Windows/Fonts/msyh.ttc",
            "C:/Windows/Fonts/simsun.ttc",
            "C:/Windows/Fonts/msyhbd.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc"
    );

    private final ProfileService profileService;
    private final InterviewReviewService interviewReviewService;
    private final List<String> fontCandidates;

    @org.springframework.beans.factory.annotation.Autowired
    public ExportService(ProfileService profileService, InterviewReviewService interviewReviewService) {
        this(profileService, interviewReviewService, FONT_CANDIDATES);
    }

    /** 测试用：可注入字体候选列表（传空列表验证 Markdown 降级路径） */
    ExportService(ProfileService profileService, InterviewReviewService interviewReviewService,
                  List<String> fontCandidates) {
        this.profileService = profileService;
        this.interviewReviewService = interviewReviewService;
        this.fontCandidates = fontCandidates;
    }

    /** 简历优化版导出：档案 + JD 匹配修改建议 */
    public ExportedFile exportResume(ExportResumeRequest request) {
        Optional<ProfileTemplate> template = request.templateId() == null || request.templateId().isBlank()
                ? profileService.active()
                : profileService.get(request.templateId());
        if (template.isEmpty()) {
            throw new IllegalArgumentException("未找到档案模板，请先在「我的档案」创建并启用");
        }

        StringBuilder md = new StringBuilder();
        md.append("# 简历优化报告\n\n");
        md.append("模板：").append(template.get().name()).append("　导出日期：").append(LocalDate.now()).append("\n\n");
        md.append("## 一、基本信息（网申填写）\n\n```\n");
        ProfileForm form = template.get().form();
        md.append(form == null ? "（未填写）" : form.toText()).append("```\n\n");
        md.append("## 二、简历全文\n\n");
        md.append(template.get().resumeText() == null || template.get().resumeText().isBlank()
                ? "（未填写简历文本）" : template.get().resumeText()).append("\n\n");

        MatchReportResponse report = request.matchReport();
        if (report != null) {
            md.append("## 三、JD 匹配修改建议\n\n");
            md.append("目标岗位：**").append(report.jobTitle()).append("**　匹配度：**")
                    .append(report.overallScore()).append("%**\n\n");
            if (report.resumeSuggestions() != null && !report.resumeSuggestions().isEmpty()) {
                int index = 1;
                for (MatchReportResponse.ResumeSuggestion suggestion : report.resumeSuggestions()) {
                    md.append(index++).append(". **原文**：").append(suggestion.original()).append("\n");
                    md.append("   **建议**：").append(suggestion.suggestion()).append("\n");
                    md.append("   **理由**：").append(suggestion.reason()).append("\n\n");
                }
            }
            if (report.summary() != null && !report.summary().isBlank()) {
                md.append("> ").append(report.summary()).append("\n");
            }
        }
        JobResumeDraft draft = request.resumeDraft();
        if (draft != null) {
            md.append("## 四、岗位定制简历建议\n\n");
            if (draft.summary() != null && !draft.summary().isBlank()) {
                md.append("### 个人简介\n\n").append(draft.summary()).append("\n\n");
            }
            if (draft.skills() != null && !draft.skills().isEmpty()) {
                md.append("### 技能顺序\n\n").append(String.join("、", draft.skills())).append("\n\n");
            }
            for (JobResumeDraft.ProjectSuggestion suggestion : draft.projectSuggestions()) {
                md.append("### 项目建议：").append(suggestion.project()).append("\n\n")
                        .append("- 原文：").append(suggestion.original()).append("\n")
                        .append("- 建议：").append(suggestion.suggestion()).append("\n")
                        .append("- 理由：").append(suggestion.reason()).append("\n\n");
            }
        }

        String baseName = "简历优化_" + sanitize(template.get().name()) + "_" + LocalDate.now();
        return render(md.toString(), request.format(), baseName);
    }

    /** 面试问答集 + 复盘导出 */
    public ExportedFile exportInterview(ExportInterviewRequest request) {
        StringBuilder md = new StringBuilder();
        String title = request.title() == null || request.title().isBlank() ? "模拟面试" : request.title().trim();
        InterviewReviewResponse review = null;
        if (request.reviewId() != null && !request.reviewId().isBlank()) {
            review = interviewReviewService.get(request.reviewId()).orElse(null);
            if (review == null) {
                throw new IllegalArgumentException("未找到复盘记录");
            }
            title = review.title();
        }
        boolean hasMessages = request.messages() != null && !request.messages().isEmpty();
        if (review == null && !hasMessages) {
            throw new IllegalArgumentException("没有可导出的面试记录");
        }

        md.append("# 模拟面试复盘\n\n");
        md.append("标题：").append(title).append("　导出日期：").append(LocalDate.now()).append("\n\n");

        if (hasMessages) {
            md.append("## 一、问答记录\n\n");
            for (InterviewReviewRequest.ReviewMessage message : request.messages()) {
                if (message == null || message.content() == null || message.content().isBlank()) {
                    continue;
                }
                md.append("**").append("user".equals(message.role()) ? "面试官" : "候选人").append("**：\n\n")
                        .append(message.content()).append("\n\n");
            }
        }

        if (review != null) {
            md.append(hasMessages ? "## 二、复盘详情\n\n" : "## 一、复盘详情\n\n");
            md.append("总分：**").append(review.overallScore()).append("**\n\n");
            md.append("### 维度评分\n\n");
            for (InterviewReviewResponse.ReviewDimension dimension : review.dimensions()) {
                md.append("- ").append(dimension.name()).append("：").append(dimension.score())
                        .append(dimension.comment().isBlank() ? "" : "（" + dimension.comment() + "）").append("\n");
            }
            if (!review.highlights().isEmpty()) {
                md.append("\n### 表现亮点\n\n");
                review.highlights().forEach(item -> md.append("- ").append(item).append("\n"));
            }
            if (!review.improvements().isEmpty()) {
                md.append("\n### 改进方向\n\n");
                review.improvements().forEach(item -> md.append("- ").append(item).append("\n"));
            }
            if (!review.perQuestion().isEmpty()) {
                md.append("\n### 逐题点评\n\n");
                for (InterviewReviewResponse.QuestionReview question : review.perQuestion()) {
                    md.append("**Q：").append(question.question()).append("**（").append(question.score()).append(" 分）\n\n")
                            .append("- 点评：").append(question.comment()).append("\n")
                            .append("- 改进：").append(question.improvement()).append("\n\n");
                }
            }
            md.append("\n> ").append(review.summary()).append("\n");
        }

        String baseName = "面试复盘_" + sanitize(title) + "_" + LocalDate.now();
        return render(md.toString(), request.format(), baseName);
    }

    // ===== 渲染：PDF（中文）优先，字体缺失降级 Markdown =====

    private ExportedFile render(String markdown, String format, String baseName) {
        if ("pdf".equalsIgnoreCase(format)) {
            try {
                byte[] pdf = renderPdf(markdown);
                return new ExportedFile(baseName + ".pdf", "application/pdf", "pdf", pdf);
            } catch (CjkFontUnavailableException e) {
                LOGGER.warn("未找到中文字体，导出降级为 Markdown");
            } catch (IOException e) {
                LOGGER.warn("PDF 生成失败，导出降级为 Markdown", e);
            }
        }
        return new ExportedFile(baseName + ".md", "text/markdown;charset=utf-8", "md",
                markdown.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] renderPdf(String markdown) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDFont font = loadCjkFont(document);
            if (font == null) {
                throw new CjkFontUnavailableException();
            }

            PDPageContentStream[] streamHolder = new PDPageContentStream[1];
            float[] yHolder = new float[]{PAGE_HEIGHT - MARGIN};
            openPage(document, streamHolder, yHolder, font);

            for (String rawLine : markdown.split("\n")) {
                String line = rawLine.strip();
                float size = 10.5f;
                if (line.startsWith("# ")) {
                    size = 16f;
                    line = line.substring(2);
                } else if (line.startsWith("## ")) {
                    size = 13f;
                    line = line.substring(3);
                } else if (line.startsWith("### ")) {
                    size = 11.5f;
                    line = line.substring(4);
                }
                line = line.replace("**", "").replace("`", "").replace("> ", "");
                if (line.isBlank()) {
                    yHolder[0] -= size * 0.7f;
                    continue;
                }
                for (String wrapped : wrap(line, (int) ((PAGE_WIDTH - 2 * MARGIN) / size))) {
                    if (yHolder[0] < MARGIN + 20) {
                        closePage(streamHolder[0]);
                        openPage(document, streamHolder, yHolder, font);
                    }
                    PDPageContentStream stream = streamHolder[0];
                    stream.beginText();
                    stream.setFont(font, size);
                    stream.newLineAtOffset(MARGIN, yHolder[0]);
                    stream.showText(wrapped);
                    stream.endText();
                    yHolder[0] -= size + 6;
                }
            }
            closePage(streamHolder[0]);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private void openPage(PDDocument document, PDPageContentStream[] holder, float[] y, PDFont font) throws IOException {
        PDPage page = new PDPage();
        document.addPage(page);
        holder[0] = new PDPageContentStream(document, page);
        y[0] = PAGE_HEIGHT - MARGIN;
    }

    private void closePage(PDPageContentStream stream) throws IOException {
        if (stream != null) {
            stream.close();
        }
    }

    private PDFont loadCjkFont(PDDocument document) throws IOException {
        for (String path : fontCandidates) {
            File file = new File(path);
            if (!file.isFile()) {
                continue;
            }
            try {
                if (path.endsWith(".ttc")) {
                    TrueTypeCollection collection = new TrueTypeCollection(file);
                    TrueTypeFont[] holder = new TrueTypeFont[1];
                    collection.processAllFonts(font -> {
                        if (holder[0] == null) {
                            holder[0] = font;
                        }
                    });
                    if (holder[0] != null) {
                        return PDType0Font.load(document, holder[0], true);
                    }
                } else {
                    return PDType0Font.load(document, file);
                }
            } catch (IOException e) {
                LOGGER.warn("字体加载失败，尝试下一个候选: {} - {}", path, e.getMessage());
            }
        }
        return null;
    }

    private List<String> wrap(String line, int charsPerLine) {
        List<String> result = new ArrayList<>();
        int width = Math.max(10, charsPerLine);
        for (int i = 0; i < line.length(); i += width) {
            result.add(line.substring(i, Math.min(line.length(), i + width)));
        }
        return result;
    }

    private String sanitize(String name) {
        return name == null ? "导出" : name.replaceAll("[\\\\/:*?\"<>|\\s]", "_");
    }

    private static class CjkFontUnavailableException extends RuntimeException {
    }
}
