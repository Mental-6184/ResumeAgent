package com.resumeagent.service;

import com.resumeagent.config.ResumeAgentProperties;
import com.resumeagent.model.AuditEntry;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final ResumeAgentProperties properties;
    private final List<AuditEntry> entries = new CopyOnWriteArrayList<>();

    public AuditService(ResumeAgentProperties properties) {
        this.properties = properties;
    }

    public void record(String category, String conversationId, String input, String output, String notes) {
        if (!properties.getAudit().isEnabled()) {
            return;
        }
        entries.add(new AuditEntry(Instant.now(), category, conversationId, input, output, notes));
    }

    public List<AuditEntry> recent(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        int size = entries.size();
        int start = Math.max(0, size - limit);
        return List.copyOf(entries.subList(start, size));
    }
}

