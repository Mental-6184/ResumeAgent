package com.resumeagent.service;

import com.resumeagent.config.ResumeAgentProperties;
import com.resumeagent.model.AuditEntry;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final ResumeAgentProperties properties;
    private final ArrayDeque<AuditEntry> entries = new ArrayDeque<>();

    public AuditService(ResumeAgentProperties properties) {
        this.properties = properties;
    }

    public void record(String category, String conversationId, String input, String output, String notes) {
        if (!properties.getAudit().isEnabled()) {
            return;
        }
        synchronized (entries) {
            entries.addLast(new AuditEntry(Instant.now(), category, conversationId, input, output, notes));
            int maxEntries = Math.max(1, properties.getAudit().getMaxEntries());
            while (entries.size() > maxEntries) {
                entries.removeFirst();
            }
        }
    }

    public List<AuditEntry> recent(int limit) {
        synchronized (entries) {
            int actualLimit = Math.max(1, Math.min(limit, 100));
            int start = Math.max(0, entries.size() - actualLimit);
            List<AuditEntry> snapshot = new ArrayList<>(entries);
            return List.copyOf(snapshot.subList(start, snapshot.size()));
        }
    }
}

