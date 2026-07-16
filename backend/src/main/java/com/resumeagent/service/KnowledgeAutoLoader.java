package com.resumeagent.service;

import com.resumeagent.dto.IngestRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * 启动时自动扫描 classpath:knowledge/ 目录，将文件加载到知识库。
 * 支持 .txt 和 .md 文件，文件名作为 sourceName，目录名作为 sourceType。
 */
@Component
public class KnowledgeAutoLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeAutoLoader.class);

    private static final Map<String, String> DIR_TYPE_MAP = Map.of(
            "resume", "resume",
            "projects", "project",
            "blog", "blog"
    );

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeAutoLoader(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Override
    public void run(String... args) {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            // 加载根目录文件 knowledge/*.txt 和 knowledge/*.md
            loadResources(resolver, "classpath:knowledge/*.txt", "resume");
            loadResources(resolver, "classpath:knowledge/*.md", "resume");

            // 加载子目录
            for (Map.Entry<String, String> entry : DIR_TYPE_MAP.entrySet()) {
                String dir = entry.getKey();
                String type = entry.getValue();
                loadResources(resolver, "classpath:knowledge/" + dir + "/*.txt", type);
                loadResources(resolver, "classpath:knowledge/" + dir + "/*.md", type);
            }
        } catch (IOException e) {
            log.warn("知识目录扫描失败（可忽略）: {}", e.getMessage());
        }
    }

    private void loadResources(PathMatchingResourcePatternResolver resolver,
                               String pattern, String defaultType) throws IOException {
        Resource[] resources;
        try {
            resources = resolver.getResources(pattern);
        } catch (IOException e) {
            // 目录不存在时静默跳过
            return;
        }

        for (Resource resource : resources) {
            if (!resource.isReadable()) continue;
            try {
                String filename = resource.getFilename();
                if (filename == null) continue;

                String sourceName = filename.contains(".")
                        ? filename.substring(0, filename.lastIndexOf('.'))
                        : filename;

                String content = readResource(resource);
                if (content.isBlank()) continue;

                // 从文件名推断 sourceType（如果包含前缀如 resume_xxx）
                String sourceType = defaultType;
                if (sourceName.contains("_")) {
                    String prefix = sourceName.split("_")[0].toLowerCase();
                    if (DIR_TYPE_MAP.containsKey(prefix)) {
                        sourceType = DIR_TYPE_MAP.get(prefix);
                    }
                }

                IngestRequest request = new IngestRequest(sourceType, sourceName, content);
                var response = knowledgeBaseService.ingest(request);
                log.info("自动加载知识文件: {} (type={}, chunks={})", filename, sourceType, response.chunks());

            } catch (Exception e) {
                log.warn("加载知识文件失败: {} - {}", resource.getFilename(), e.getMessage());
            }
        }
    }

    private String readResource(Resource resource) throws IOException {
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
