package com.resumeagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 知识库手动文档持久化单测：落盘、重启重载、删除、内置文档只读保护。
 */
class KnowledgeDocumentServiceTest {

    @TempDir
    Path tempDir;

    private KnowledgeBaseService knowledgeBaseService;
    private KnowledgeDocumentService documentService;

    @BeforeEach
    void setUp() {
        knowledgeBaseService = new KnowledgeBaseService(new EmbeddingService());
        documentService = new KnowledgeDocumentService(knowledgeBaseService, new ObjectMapper(), tempDir);
    }

    @Test
    void savedDocumentShouldEnterKnowledgeBaseAndListed() {
        documentService.save("project", "星河监控平台", "星河监控平台：基于 Prometheus 的微服务监控系统。");

        assertThat(knowledgeBaseService.searchBySourceName("星河监控平台")).isNotEmpty();
        assertThat(documentService.list())
                .anyMatch(doc -> doc.sourceName().equals("星河监控平台") && !doc.builtin() && doc.chunks() > 0);
    }

    @Test
    void uploadedDocumentsShouldSurviveRestart() {
        documentService.save("project", "星河监控平台", "星河监控平台：基于 Prometheus 的微服务监控系统。");

        // 模拟重启：新知识库 + 新服务实例（同一存储目录）
        KnowledgeBaseService reloadedKb = new KnowledgeBaseService(new EmbeddingService());
        new KnowledgeDocumentService(reloadedKb, new ObjectMapper(), tempDir);

        assertThat(reloadedKb.searchBySourceName("星河监控平台")).isNotEmpty();
    }

    @Test
    void deletedDocumentShouldLeaveKnowledgeBaseAndDisk() {
        documentService.save("blog", "调试技巧", "关于远程调试的一篇文章。");
        assertThat(documentService.delete("调试技巧")).isTrue();

        assertThat(knowledgeBaseService.searchBySourceName("调试技巧")).isEmpty();
        assertThat(documentService.list()).noneMatch(doc -> doc.sourceName().equals("调试技巧"));
        // 再删返回 false
        assertThat(documentService.delete("调试技巧")).isFalse();
    }

    @Test
    void builtinDocumentsShouldNotBeDeleted() {
        documentService.markBuiltin("崔哲航-个人信息");
        knowledgeBaseService.ingest(new com.resumeagent.dto.IngestRequest(
                "resume", "崔哲航-个人信息", "内置简历内容。"));

        assertThat(documentService.isBuiltin("崔哲航-个人信息")).isTrue();
        assertThat(documentService.delete("崔哲航-个人信息")).isFalse();
        assertThat(knowledgeBaseService.searchBySourceName("崔哲航-个人信息")).isNotEmpty();
        assertThat(documentService.list())
                .anyMatch(doc -> doc.sourceName().equals("崔哲航-个人信息") && doc.builtin());
    }

    @Test
    void savingSameNameShouldReplaceNotDuplicate() {
        documentService.save("project", "同名文档", "第一版内容：苹果。");
        documentService.save("project", "同名文档", "第二版内容：香蕉。");

        var items = knowledgeBaseService.searchBySourceName("同名文档");
        assertThat(items).noneMatch(item -> item.content().contains("苹果"));
        assertThat(items).anyMatch(item -> item.content().contains("香蕉"));
    }
}
