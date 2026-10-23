package com.resumeagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.resumeagent.dto.IngestRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 知识库检索层单测：两阶段检索、动态元数据入口、IDF 关键词抽取。
 */
class KnowledgeBaseServiceTest {

    private final KnowledgeBaseService service = new KnowledgeBaseService(new EmbeddingService());

    @Test
    void shouldRetrieveIngestedContentViaInvertedIndex() {
        service.ingest(new IngestRequest("project", "ZetaCloud 分布式日志平台",
                "ZetaCloud 是一个分布式日志平台，采用 Kafka 与 Elasticsearch 构建日志管道，支持亿级日志检索。"));

        var result = service.search("ZetaCloud 日志平台");

        assertThat(result.items()).isNotEmpty();
        assertThat(result.items()).anyMatch(item -> item.content().contains("ZetaCloud"));
    }

    @Test
    void shouldKeepSearchResultsStableAcrossMultipleIngests() {
        service.ingest(new IngestRequest("resume", "个人简历", "负责 Java 后端开发与安全能力建设。\n\n参与网络安全项目设计，覆盖权限校验、风控和日志审计。"));
        service.ingest(new IngestRequest("project", "另一个项目", "这是一个与检索无关的文本，讲的是绘画和音乐。"));

        var response = service.search("网络安全项目");

        assertThat(response.items()).isNotEmpty();
        assertThat(response.items().stream().map(item -> item.content()).toList())
                .anyMatch(content -> content.contains("网络安全项目") || content.contains("权限校验"));
    }

    @Test
    void shouldListDistinctSourceNames() {
        service.ingest(new IngestRequest("project", "项目甲", "甲的内容。\n\n第二段。"));
        service.ingest(new IngestRequest("project", "项目乙", "乙的内容。"));

        assertThat(service.sourceNames()).containsExactlyInAnyOrder("项目甲", "项目乙");
    }

    @Test
    void shouldLimitChunksPerSourceWhenSearchingByType() {
        service.ingest(new IngestRequest("project", "项目甲", "第一段。\n\n第二段。\n\n第三段。"));
        service.ingest(new IngestRequest("project", "项目乙", "乙的内容。"));

        List<com.resumeagent.dto.KnowledgeSearchItem> items = service.searchBySourceType("project", 1);

        assertThat(items).hasSize(2);
        assertThat(items.stream().map(item -> item.sourceName())).containsExactlyInAnyOrder("项目甲", "项目乙");
    }

    @Test
    void noOverlapQueryShouldReturnEmptyInsteadOfCosineNoise() {
        service.ingest(new IngestRequest("project", "星河监控平台", "基于 Prometheus 的微服务监控系统。"));

        // 与任何文档零词元重叠的查询（如"你好"）：不得用余弦噪声凑结果
        assertThat(service.search("你好").items()).isEmpty();
        assertThat(service.search("量子色动力学").items()).isEmpty();
    }

    @Test
    void shouldExtractDistinctiveKeywordsAndDropHighFrequencyTerms() {
        for (int i = 0; i < 5; i++) {
            service.ingest(new IngestRequest("blog", "通用文章" + i,
                    "这是一篇关于日志平台的通用文章，日志平台这个词出现在每篇文章里。"));
        }
        service.ingest(new IngestRequest("project", "ZetaCloud 分布式日志平台",
                "ZetaCloud 是一个分布式日志平台，采用 Kafka 构建日志管道。"));

        List<String> keywords = service.extractKeywords("zetacloud 日志平台", 5);

        // 有区分度的词被抽出
        assertThat(keywords).contains("zetacloud");
        // 出现在所有 chunk 中的高频词被过滤
        assertThat(keywords).doesNotContain("日志平台");
    }
}
