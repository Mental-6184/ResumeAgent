package com.resumeagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.resumeagent.config.ResumeAgentProperties;
import org.junit.jupiter.api.Test;

class AuditServiceTest {

    @Test
    void keepsBoundedEntriesAndClampsRecentLimit() {
        ResumeAgentProperties properties = new ResumeAgentProperties();
        properties.getAudit().setMaxEntries(2);
        AuditService service = new AuditService(properties);

        service.record("chat", "one", "input-1", "output-1", "");
        service.record("chat", "two", "input-2", "output-2", "");
        service.record("chat", "three", "input-3", "output-3", "");

        assertThat(service.recent(1000)).hasSize(2);
        assertThat(service.recent(0)).hasSize(1);
        assertThat(service.recent(-1)).hasSize(1);
    }
}
