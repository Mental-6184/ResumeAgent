package com.resumeagent.controller;

import com.resumeagent.dto.*;
import com.resumeagent.service.AgentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/analyze")
    public JobAnalyzeResponse analyze(@Valid @RequestBody JobAnalyzeRequest request) {
        return agentService.analyze(request);
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
}

