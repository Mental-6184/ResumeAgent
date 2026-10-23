package com.resumeagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeagent.dto.JobSaveRequest;
import com.resumeagent.dto.MatchReportRequest;
import com.resumeagent.dto.MatchReportResponse;
import com.resumeagent.dto.InterviewReviewSummary;
import com.resumeagent.model.JobRecord;
import com.resumeagent.model.JobResumeDraft;
import com.resumeagent.model.JobStatus;
import com.resumeagent.model.ProfileTemplate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class JobService {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobService.class);
    private static final int MAX_JOBS = 500;

    private final ObjectMapper objectMapper;
    private final MatchReportService matchReportService;
    private final ProfileService profileService;
    private final InterviewReviewService interviewReviewService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final Path storageDir;
    private final Object lock = new Object();
    private final Map<String, JobRecord> jobs = new LinkedHashMap<>();

    @Autowired
    public JobService(ObjectMapper objectMapper,
                      MatchReportService matchReportService,
                      ProfileService profileService,
                      InterviewReviewService interviewReviewService,
                      KnowledgeBaseService knowledgeBaseService) {
        this(objectMapper, matchReportService, profileService, interviewReviewService,
                knowledgeBaseService, DataDirs.resolve().resolve("jobs"));
    }

    public JobService(ObjectMapper objectMapper,
                      MatchReportService matchReportService,
                      ProfileService profileService,
                      InterviewReviewService interviewReviewService,
                      KnowledgeBaseService knowledgeBaseService,
                      Path storageDir) {
        this.objectMapper = objectMapper;
        this.matchReportService = matchReportService;
        this.profileService = profileService;
        this.interviewReviewService = interviewReviewService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.storageDir = storageDir;
        loadFromDisk();
    }

    public List<JobRecord> list(String status, String query) {
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        synchronized (lock) {
            return jobs.values().stream()
                    .filter(job -> normalizedStatus.isBlank()
                            || job.status().name().equals(normalizedStatus))
                    .filter(job -> normalizedQuery.isBlank()
                            || contains(job.company(), normalizedQuery)
                            || contains(job.title(), normalizedQuery)
                            || contains(job.jobDescription(), normalizedQuery))
                    .sorted(Comparator.comparingLong(JobRecord::updatedAt).reversed())
                    .toList();
        }
    }

    public Optional<JobRecord> get(String id) {
        synchronized (lock) {
            return Optional.ofNullable(jobs.get(id));
        }
    }

    public JobRecord create(JobSaveRequest request) {
        synchronized (lock) {
            if (jobs.size() >= MAX_JOBS) {
                throw new IllegalStateException("岗位数量已达到上限，请先删除旧岗位");
            }
            long now = System.currentTimeMillis();
            JobRecord job = new JobRecord(UUID.randomUUID().toString(), clean(request.company()),
                    clean(request.title()), clean(request.location()), clean(request.salaryRange()),
                    clean(request.url()), clean(request.jobDescription()), request.deadline(),
                    request.status() == null ? JobStatus.SAVED : request.status(), clean(request.notes()),
                    null, null, null, List.of(), now, now);
            publish(job);
            return job;
        }
    }

    public Optional<JobRecord> update(String id, JobSaveRequest request) {
        synchronized (lock) {
            JobRecord existing = jobs.get(id);
            if (existing == null) {
                return Optional.empty();
            }
            JobRecord updated = new JobRecord(existing.id(), clean(request.company()), clean(request.title()),
                    clean(request.location()), clean(request.salaryRange()), clean(request.url()),
                    clean(request.jobDescription()), request.deadline(),
                    request.status() == null ? existing.status() : request.status(), clean(request.notes()),
                    existing.profileTemplateId(), existing.matchReport(), existing.resumeDraft(),
                    existing.reviewIds(), existing.createdAt(), System.currentTimeMillis());
            publish(updated);
            return Optional.of(updated);
        }
    }

    public Optional<JobRecord> updateStatus(String id, JobStatus status) {
        synchronized (lock) {
            JobRecord existing = jobs.get(id);
            if (existing == null) {
                return Optional.empty();
            }
            JobRecord updated = copy(existing, existing.company(), existing.title(), existing.location(),
                    existing.salaryRange(), existing.url(), existing.jobDescription(), existing.deadline(),
                    status, existing.notes(), existing.profileTemplateId(), existing.matchReport(),
                    existing.resumeDraft(), existing.reviewIds());
            publish(updated);
            return Optional.of(updated);
        }
    }

    public boolean delete(String id) {
        synchronized (lock) {
            if (!jobs.containsKey(id)) {
                return false;
            }
            try {
                Files.deleteIfExists(storageDir.resolve(id + ".json"));
            } catch (IOException e) {
                LOGGER.warn("删除岗位文件失败: {}", e.getMessage());
                throw new IllegalStateException("岗位删除失败");
            }
            jobs.remove(id);
            return true;
        }
    }

    public Optional<JobRecord> generateMatchReport(String id) {
        JobRecord job = require(id);
        ProfileTemplate profile = profileService.active()
                .orElseThrow(() -> new IllegalStateException("请先在我的档案中激活一个档案模板"));
        MatchReportResponse report = matchReportService.analyze(
                new MatchReportRequest(job.title(), job.jobDescription()));
        synchronized (lock) {
            JobRecord current = jobs.get(id);
            if (current == null) return Optional.empty();
            JobRecord updated = copy(current, current.company(), current.title(), current.location(),
                    current.salaryRange(), current.url(), current.jobDescription(), current.deadline(),
                    current.status(), current.notes(), profile.id(), report, current.resumeDraft(),
                    current.reviewIds());
            publish(updated);
            return Optional.of(updated);
        }
    }

    public Optional<JobRecord> generateResumeDraft(String id) {
        JobRecord job = require(id);
        if (job.matchReport() == null) {
            throw new IllegalStateException("请先生成岗位匹配报告");
        }
        ProfileTemplate profile = profileService.active()
                .orElseThrow(() -> new IllegalStateException("请先在我的档案中激活一个档案模板"));
        JobResumeDraft draft = buildDraft(profile, job);
        return saveDraft(id, draft);
    }

    public Optional<JobRecord> saveDraft(String id, JobResumeDraft draft) {
        synchronized (lock) {
            JobRecord existing = jobs.get(id);
            if (existing == null) return Optional.empty();
            JobResumeDraft saved = new JobResumeDraft(clean(draft.summary()),
                    safeList(draft.skills()), safeSuggestions(draft.projectSuggestions()),
                    safeList(draft.keywords()), true, System.currentTimeMillis());
            JobRecord updated = copy(existing, existing.company(), existing.title(), existing.location(),
                    existing.salaryRange(), existing.url(), existing.jobDescription(), existing.deadline(),
                    existing.status(), existing.notes(), existing.profileTemplateId(), existing.matchReport(),
                    saved, existing.reviewIds());
            publish(updated);
            return Optional.of(updated);
        }
    }

    public List<InterviewReviewSummary> reviews(String id) {
        JobRecord job = require(id);
        List<InterviewReviewSummary> result = new ArrayList<>();
        for (String reviewId : job.reviewIds()) {
            interviewReviewService.get(reviewId).ifPresent(review -> result.add(
                    new InterviewReviewSummary(review.id(), review.title(), review.createdAt(), review.overallScore())));
        }
        return result;
    }

    public Optional<JobRecord> linkReview(String id, String reviewId) {
        if (get(id).isEmpty()) {
            return Optional.empty();
        }
        if (interviewReviewService.get(reviewId).isEmpty()) {
            throw new IllegalArgumentException("复盘不存在");
        }
        synchronized (lock) {
            JobRecord existing = jobs.get(id);
            if (existing == null) return Optional.empty();
            List<String> reviewIds = new ArrayList<>(existing.reviewIds());
            if (!reviewIds.contains(reviewId)) reviewIds.add(reviewId);
            JobRecord updated = copy(existing, existing.company(), existing.title(), existing.location(),
                    existing.salaryRange(), existing.url(), existing.jobDescription(), existing.deadline(),
                    existing.status(), existing.notes(), existing.profileTemplateId(), existing.matchReport(),
                    existing.resumeDraft(), reviewIds);
            publish(updated);
            return Optional.of(updated);
        }
    }

    public Optional<JobRecord> unlinkReview(String id, String reviewId) {
        synchronized (lock) {
            JobRecord existing = jobs.get(id);
            if (existing == null) return Optional.empty();
            List<String> reviewIds = new ArrayList<>(existing.reviewIds());
            reviewIds.remove(reviewId);
            JobRecord updated = copy(existing, existing.company(), existing.title(), existing.location(),
                    existing.salaryRange(), existing.url(), existing.jobDescription(), existing.deadline(),
                    existing.status(), existing.notes(), existing.profileTemplateId(), existing.matchReport(),
                    existing.resumeDraft(), reviewIds);
            publish(updated);
            return Optional.of(updated);
        }
    }

    private JobResumeDraft buildDraft(ProfileTemplate profile, JobRecord job) {
        List<String> skills = new ArrayList<>();
        if (profile.form() != null && profile.form().skills() != null) {
            for (String skill : profile.form().skills().split("[,，、;；\\n]+")) {
                if (!skill.isBlank()) skills.add(skill.trim());
            }
        }
        List<String> keywords = knowledgeBaseService.extractKeywords(job.jobDescription(), 12);
        List<JobResumeDraft.ProjectSuggestion> suggestions = job.matchReport().resumeSuggestions().stream()
                .map(item -> new JobResumeDraft.ProjectSuggestion("项目经历", item.original(),
                        item.suggestion(), item.reason()))
                .toList();
        String summary = profile.form() != null && profile.form().selfEvaluation() != null
                ? profile.form().selfEvaluation().trim() : "";
        if (summary.isBlank() && profile.resumeText() != null) {
            summary = profile.resumeText().trim();
            if (summary.length() > 300) summary = summary.substring(0, 300);
        }
        return new JobResumeDraft(summary, skills, suggestions, keywords, false, System.currentTimeMillis());
    }

    private JobRecord require(String id) {
        return get(id).orElseThrow(() -> new IllegalArgumentException("岗位不存在"));
    }

    private JobRecord copy(JobRecord source, String company, String title, String location, String salary,
                           String url, String description, Long deadline, JobStatus status, String notes,
                           String profileId, MatchReportResponse report, JobResumeDraft draft,
                           List<String> reviews) {
        return new JobRecord(source.id(), company, title, location, salary, url, description, deadline, status,
                notes, profileId, report, draft, reviews, source.createdAt(), System.currentTimeMillis());
    }

    private void publish(JobRecord job) {
        if (!persist(job)) throw new IllegalStateException("岗位持久化失败");
        jobs.put(job.id(), job);
    }

    private boolean persist(JobRecord job) {
        Path temporary = null;
        try {
            Files.createDirectories(storageDir);
            Path target = storageDir.resolve(job.id() + ".json");
            temporary = Files.createTempFile(storageDir, "job-", ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), job);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            LOGGER.warn("持久化岗位失败: {}", e.getMessage());
            return false;
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
        }
    }

    private void loadFromDisk() {
        if (!Files.isDirectory(storageDir)) return;
        try (Stream<Path> files = Files.list(storageDir)) {
            files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            JobRecord job = objectMapper.readValue(path.toFile(), JobRecord.class);
                            jobs.put(job.id(), job);
                        } catch (IOException | RuntimeException e) {
                            LOGGER.warn("读取岗位失败: {}", path.getFileName());
                        }
                    });
        } catch (IOException e) {
            LOGGER.warn("扫描岗位目录失败: {}", e.getMessage());
        }
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private String clean(String value) { return value == null ? "" : value.trim(); }

    private List<String> safeList(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).toList();
    }

    private List<JobResumeDraft.ProjectSuggestion> safeSuggestions(List<JobResumeDraft.ProjectSuggestion> values) {
        if (values == null) return List.of();
        return values.stream().filter(value -> value != null).toList();
    }
}
