package com.resumeagent.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.resumeagent.service.KnowledgeBaseService;
import com.resumeagent.service.KnowledgeDocumentService;
import com.resumeagent.service.PdfTextExtractor;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class RagControllerTest {

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @Mock
    private KnowledgeDocumentService documentService;

    @Mock
    private PdfTextExtractor pdfTextExtractor;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new RagController(knowledgeBaseService, documentService, pdfTextExtractor)).build();
        when(knowledgeBaseService.searchBySourceName(any())).thenReturn(List.of());
    }

    @Test
    void shouldAcceptTextDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.txt", "text/plain", "Java backend experience".getBytes());

        mockMvc.perform(multipart("/api/rag/upload").file(file))
                .andExpect(status().isOk());

        verify(documentService).save("project", "resume", "Java backend experience");
    }

    @Test
    void shouldRejectUnsupportedDocumentType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/rag/upload").file(file))
                .andExpect(status().isBadRequest());
    }
}
