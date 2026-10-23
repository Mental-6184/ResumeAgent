package com.resumeagent.controller;

import com.resumeagent.dto.*;
import com.resumeagent.service.AgentService;
import com.resumeagent.service.InterviewReviewService;
import com.resumeagent.service.MatchReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;
    private final MatchReportService matchReportService;
    private final InterviewReviewService interviewReviewService;

    public AgentController(AgentService agentService,
                           MatchReportService matchReportService,
                           InterviewReviewService interviewReviewService) {
        this.agentService = agentService;
        this.matchReportService = matchReportService;
        this.interviewReviewService = interviewReviewService;
    }

    @PostMapping("/analyze")
    public JobAnalyzeResponse analyze(@Valid @RequestBody JobAnalyzeRequest request) {
        return agentService.analyze(request);
    }

    /** 简历/JD 深度匹配报告：逐条 JD 要求 + 维度分 + 简历修改建议 */
    @PostMapping("/match-report")
    public MatchReportResponse matchReport(@Valid @RequestBody MatchReportRequest request) {
        return matchReportService.analyze(request);
    }

    @PostMapping("/project-query")
    public ProjectQueryResponse queryProjects(@Valid @RequestBody ProjectQueryRequest request) {
        return agentService.queryProjects(request);
    }

    @PostMapping("/skill-analysis")
    public SkillAnalysisResponse analyzeSkills(@Valid @RequestBody SkillAnalysisRequest request) {
        return agentService.analyzeSkills(request);
    }

    @PostMapping("/interview-answer")
    public InterviewAnswerResponse generateInterviewAnswer(@Valid @RequestBody InterviewAnswerRequest request) {
        return agentService.generateInterviewAnswer(request);
    }

    @GetMapping("/tech-quote")
    public TechQuoteResponse getTechQuote() {
        return agentService.generateTechQuote();
    }

    // ===== 模拟面试复盘 =====

    /** 生成复盘并存档 */
    @PostMapping("/interview-reviews")
    public InterviewReviewResponse createReview(@Valid @RequestBody InterviewReviewRequest request) {
        try {
            return interviewReviewService.review(request);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /** 复盘存档列表（多场对比） */
    @GetMapping("/interview-reviews")
    public java.util.List<InterviewReviewSummary> listReviews() {
        return interviewReviewService.list();
    }

    @GetMapping("/interview-reviews/{id}")
    public InterviewReviewResponse getReview(@PathVariable String id) {
        return interviewReviewService.get(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "复盘不存在"));
    }

    @DeleteMapping("/interview-reviews/{id}")
    public java.util.Map<String, Object> deleteReview(@PathVariable String id) {
        if (!interviewReviewService.delete(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "复盘不存在");
        }
        return java.util.Map.of("success", true);
    }
}

