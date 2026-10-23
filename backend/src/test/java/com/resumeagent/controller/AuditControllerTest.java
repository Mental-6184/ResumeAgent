package com.resumeagent.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resumeagent.config.ResumeAgentProperties;
import com.resumeagent.model.AuditEntry;
import com.resumeagent.service.AuditService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuditControllerTest {

    @Mock
    private AuditService auditService;

    private ResumeAgentProperties properties;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        properties = new ResumeAgentProperties();
        properties.setAdminToken("test-admin-token");
        mockMvc = MockMvcBuilders.standaloneSetup(new AuditController(auditService, properties)).build();
    }

    @Test
    void shouldRejectMissingToken() throws Exception {
        mockMvc.perform(get("/api/audit/logs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectWrongToken() throws Exception {
        mockMvc.perform(get("/api/audit/logs")
                        .header("Authorization", "Bearer wrong-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowCorrectToken() throws Exception {
        when(auditService.recent(20)).thenReturn(List.of(
                new AuditEntry(Instant.now(), "test", "conversation", "input", "output", "notes")));

        mockMvc.perform(get("/api/audit/logs")
                        .header("Authorization", "Bearer test-admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectWhenTokenIsNotConfigured() throws Exception {
        properties.setAdminToken("");

        mockMvc.perform(get("/api/audit/logs")
                        .header("Authorization", "Bearer test-admin-token"))
                .andExpect(status().isUnauthorized());
    }
}
