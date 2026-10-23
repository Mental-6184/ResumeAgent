package com.resumeagent.controller;

import com.resumeagent.config.ResumeAgentProperties;
import com.resumeagent.dto.AuditEntryResponse;
import com.resumeagent.service.AuditService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;
    private final ResumeAgentProperties properties;

    public AuditController(AuditService auditService, ResumeAgentProperties properties) {
        this.auditService = auditService;
        this.properties = properties;
    }

    @GetMapping("/logs")
    public List<AuditEntryResponse> logs(
            @RequestParam(defaultValue = "20") int limit,
            @org.springframework.web.bind.annotation.RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
            String authorization) {
        requireAdminToken(authorization);
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

    private void requireAdminToken(String authorization) {
        String expected = properties.getAdminToken();
        if (expected == null || expected.isBlank() || authorization == null
                || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "需要管理员访问令牌");
        }

        String actual = authorization.substring(7).trim();
        if (actual.isEmpty() || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "管理员访问令牌无效");
        }
    }
}

