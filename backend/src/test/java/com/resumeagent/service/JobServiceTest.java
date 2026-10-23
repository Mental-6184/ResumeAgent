package com.resumeagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeagent.dto.JobSaveRequest;
import com.resumeagent.dto.MatchReportResponse;
import com.resumeagent.model.ProfileForm;
import com.resumeagent.model.ProfileTemplate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JobServiceTest {

    @Test
    void persistsJobsAndReloadsThem() throws Exception {
        Path dir = Files.createTempDirectory("resumeagent-jobs-");
        MatchReportService reports = mock(MatchReportService.class);
        ProfileService profiles = mock(ProfileService.class);
        InterviewReviewService reviews = mock(InterviewReviewService.class);
        JobService service = new JobService(new ObjectMapper(), reports, profiles, reviews,
                new KnowledgeBaseService(new EmbeddingService()), dir);

        var created = service.create(request("Example", "Java 工程师", "Spring Boot 后端服务"));
        assertThat(service.list(null, "java")).extracting(job -> job.id()).containsExactly(created.id());

        JobService reloaded = new JobService(new ObjectMapper(), reports, profiles, reviews,
                new KnowledgeBaseService(new EmbeddingService()), dir);
        assertThat(reloaded.get(created.id())).isPresent();
        assertThat(reloaded.get(created.id()).orElseThrow().company()).isEqualTo("Example");
    }

    @Test
    void generatesAndSavesResumeDraftFromMatchReport() {
        Path dir;
        try {
            dir = Files.createTempDirectory("resumeagent-jobs-");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        MatchReportService reports = mock(MatchReportService.class);
        ProfileService profiles = mock(ProfileService.class);
        InterviewReviewService reviews = mock(InterviewReviewService.class);
        ProfileTemplate profile = new ProfileTemplate("profile-1", "默认档案",
                new ProfileForm("", "", "", "", "", "", "", "", "", "", "Java, Redis", "", "", "", ""),
                "Java 后端开发经历", "resume.md", 1L, 1L);
        when(profiles.active()).thenReturn(Optional.of(profile));
        when(reports.analyze(org.mockito.ArgumentMatchers.any())).thenReturn(
                new MatchReportResponse("Java 工程师", 80, List.of(), List.of(),
                        List.of(new MatchReportResponse.ResumeSuggestion("原项目描述", "改写后的项目描述", "突出岗位关键词")),
                        "匹配良好", true));

        JobService service = new JobService(new ObjectMapper(), reports, profiles, reviews,
                new KnowledgeBaseService(new EmbeddingService()), dir);
        var job = service.create(request("Example", "Java 工程师", "Spring Boot Redis"));
        var withReport = service.generateMatchReport(job.id()).orElseThrow();
        var draft = service.generateResumeDraft(withReport.id()).orElseThrow().resumeDraft();

        assertThat(draft.skills()).containsExactly("Java", "Redis");
        assertThat(draft.projectSuggestions()).hasSize(1);
    }

    private JobSaveRequest request(String company, String title, String description) {
        return new JobSaveRequest(company, title, "昆明", "15K-25K", "", description,
                null, null, "");
    }
}
