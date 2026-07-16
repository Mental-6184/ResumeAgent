package com.resumeagent.controller;

import com.resumeagent.dto.AuditEntryResponse;
import com.resumeagent.service.AuditService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/logs")
    public List<AuditEntryResponse> logs(@RequestParam(defaultValue = "20") int limit) {
        return auditService.recent(limit).stream()
                .map(entry -> new AuditEntryResponse(
                        entry.timestamp().toString(),
                        entry.category(),
                        entry.conversationId(),
                        entry.input(),
                        entry.output(),
                        entry.notes()
                ))
                .toList();
    }
}

