package com.resumeagent.controller;

import com.resumeagent.dto.IngestRequest;
import com.resumeagent.dto.IngestResponse;
import com.resumeagent.dto.KnowledgeSearchResponse;
import com.resumeagent.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final KnowledgeBaseService knowledgeBaseService;

    public RagController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping("/ingest")
    public IngestResponse ingest(@Valid @RequestBody IngestRequest request) {
        return knowledgeBaseService.ingest(request);
    }

    @GetMapping("/search")
    public KnowledgeSearchResponse search(@RequestParam String query) {
        return knowledgeBaseService.search(query);
    }
}

