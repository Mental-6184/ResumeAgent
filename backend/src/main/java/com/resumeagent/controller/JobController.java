package com.resumeagent.controller;

import com.resumeagent.dto.JobReviewLinkRequest;
import com.resumeagent.dto.JobSaveRequest;
import com.resumeagent.dto.JobStatusRequest;
import com.resumeagent.model.JobRecord;
import com.resumeagent.model.JobResumeDraft;
import com.resumeagent.service.JobService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Validated
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping
    public List<JobRecord> list(@RequestParam(required = false) @Size(max = 30) String status,
                                @RequestParam(required = false) @Size(max = 100) String q) {
        return jobService.list(status, q);
    }

    @PostMapping
    public JobRecord create(@Valid @RequestBody JobSaveRequest request) {
        return runBadRequest(() -> jobService.create(request));
    }

    @GetMapping("/{id}")
    public JobRecord get(@PathVariable String id) {
        return jobService.get(id).orElseThrow(() -> notFound("岗位不存在"));
    }

    @PutMapping("/{id}")
    public JobRecord update(@PathVariable String id, @Valid @RequestBody JobSaveRequest request) {
        return runNotFound(() -> jobService.update(id, request), "岗位不存在");
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        if (!jobService.delete(id)) throw notFound("岗位不存在");
        return Map.of("success", true);
    }

    @PatchMapping("/{id}/status")
    public JobRecord updateStatus(@PathVariable String id, @Valid @RequestBody JobStatusRequest request) {
        return runNotFound(() -> jobService.updateStatus(id, request.status()), "岗位不存在");
    }

    @PostMapping("/{id}/match-report")
    public JobRecord matchReport(@PathVariable String id) {
        return runBusiness(() -> jobService.generateMatchReport(id));
    }

    @GetMapping("/{id}/match-report")
    public Object getMatchReport(@PathVariable String id) {
        JobRecord job = jobService.get(id).orElseThrow(() -> notFound("岗位不存在"));
        if (job.matchReport() == null) throw notFound("岗位匹配报告不存在");
        return job.matchReport();
    }

    @PostMapping("/{id}/resume-draft")
    public JobResumeDraft generateResumeDraft(@PathVariable String id) {
        JobRecord job = runBusiness(() -> jobService.generateResumeDraft(id));
        return job.resumeDraft();
    }

    @PutMapping("/{id}/resume-draft")
    public JobResumeDraft saveResumeDraft(@PathVariable String id, @RequestBody JobResumeDraft draft) {
        JobRecord job = runNotFound(() -> jobService.saveDraft(id, draft), "岗位不存在");
        return job.resumeDraft();
    }

    @GetMapping("/{id}/reviews")
    public Object reviews(@PathVariable String id) {
        try {
            return jobService.reviews(id);
        } catch (IllegalArgumentException e) {
            throw notFound(e.getMessage());
        }
    }

    @PostMapping("/{id}/reviews")
    public JobRecord linkReview(@PathVariable String id, @Valid @RequestBody JobReviewLinkRequest request) {
        try {
            return jobService.linkReview(id, request.reviewId())
                    .orElseThrow(() -> notFound("岗位不存在"));
        } catch (IllegalArgumentException e) {
            throw notFound(e.getMessage());
        }
    }

    @DeleteMapping("/{id}/reviews/{reviewId}")
    public JobRecord unlinkReview(@PathVariable String id, @PathVariable String reviewId) {
        return runNotFound(() -> jobService.unlinkReview(id, reviewId), "岗位不存在");
    }

    private <T> T runBadRequest(java.util.function.Supplier<T> action) {
        try { return action.get(); }
        catch (IllegalStateException | IllegalArgumentException e) { throw badRequest(e.getMessage()); }
    }

    private <T> T runBusiness(java.util.function.Supplier<java.util.Optional<T>> action) {
        try {
            return action.get().orElseThrow(() -> notFound("岗位不存在"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            if ("岗位不存在".equals(e.getMessage())) throw notFound(e.getMessage());
            throw badRequest(e.getMessage());
        }
    }

    private <T> T runNotFound(java.util.function.Supplier<java.util.Optional<T>> action, String message) {
        return action.get().orElseThrow(() -> notFound(message));
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
