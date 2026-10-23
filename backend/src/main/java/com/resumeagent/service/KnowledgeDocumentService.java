package com.resumeagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeagent.dto.IngestRequest;
import com.resumeagent.dto.KnowledgeDocumentInfo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 知识库手动文档管理：页面上传/粘贴的文档落盘持久化（data/knowledge/），
 * 启动时自动重新入库，重启不丢。
 *
 * 文档分两类：
 * - builtin：classpath:knowledge/ 由 {@link KnowledgeAutoLoader} 自动加载（只读，不可删除）
 * - manual：  用户上传，可删除、可持久化
 */
@Service
public class KnowledgeDocumentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeDocumentService.class);

    /** 磁盘上的手动文档 */
    public record StoredDocument(String sourceName, String sourceType, String content, long uploadedAt) {
    }

    private final KnowledgeBaseService knowledgeBaseService;
    private final ObjectMapper objectMapper;
    private final Path storageDir;

    private final Object lock = new Object();
    private final Set<String> manualNames = new LinkedHashSet<>();
    private final Set<String> builtinNames = new LinkedHashSet<>();
    private final Map<String, StoredDocument> manualDocuments = new LinkedHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public KnowledgeDocumentService(KnowledgeBaseService knowledgeBaseService, ObjectMapper objectMapper) {
        this(knowledgeBaseService, objectMapper, DataDirs.resolve().resolve("knowledge"));
    }

    public KnowledgeDocumentService(KnowledgeBaseService knowledgeBaseService, ObjectMapper objectMapper, Path storageDir) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.objectMapper = objectMapper;
        this.storageDir = storageDir;
        loadFromDisk();
    }

    /** 标记内置文档（由 KnowledgeAutoLoader 调用），内置文档只读不可删 */
    public void markBuiltin(String sourceName) {
        synchronized (lock) {
            builtinNames.add(sourceName);
        }
    }

    /** 保存（新增/覆盖）手动文档：入库 + 落盘；同名旧内容先移除，避免重复块 */
    public void save(String sourceType, String sourceName, String content) {
        String cleanName = sourceName == null ? "" : sourceName.trim();
        String cleanType = sourceType == null || sourceType.isBlank() ? "project" : sourceType.trim();
        String cleanContent = content == null ? "" : content.trim();
        StoredDocument stored = new StoredDocument(cleanName, cleanType, cleanContent, System.currentTimeMillis());
        if (!persist(stored)) {
            throw new IllegalStateException("知识文档持久化失败");
        }
        synchronized (lock) {
            knowledgeBaseService.removeBySourceName(cleanName);
            knowledgeBaseService.ingest(new IngestRequest(cleanType, cleanName, cleanContent));
            manualNames.add(cleanName);
            builtinNames.remove(cleanName);
            manualDocuments.put(cleanName, stored);
        }
    }

    /** 删除手动文档（内置文档拒绝删除）；返回 false 表示文档不存在或是内置文档 */
    public boolean delete(String sourceName) {
        boolean deleted;
        synchronized (lock) {
            if (builtinNames.contains(sourceName) || !manualNames.contains(sourceName)) {
                return false;
            }
            manualNames.remove(sourceName);
            manualDocuments.remove(sourceName);
            deleted = true;
        }
        knowledgeBaseService.removeBySourceName(sourceName);
        try {
            Files.deleteIfExists(storageDir.resolve(fileName(sourceName)));
        } catch (IOException e) {
            LOGGER.warn("删除知识文档文件失败，可重试: {}", e.getMessage());
        }
        return deleted;
    }

    /** 文档列表（内置 + 手动），含块数与来源标记 */
    public List<KnowledgeDocumentInfo> list() {
        synchronized (lock) {
            List<KnowledgeDocumentInfo> items = new ArrayList<>();
            for (StoredDocument doc : manualDocuments.values()) {
                items.add(new KnowledgeDocumentInfo(doc.sourceName(), doc.sourceType(),
                        knowledgeBaseService.searchBySourceName(doc.sourceName()).size(), doc.uploadedAt(), false));
            }
            for (String name : builtinNames) {
                if (manualNames.contains(name)) {
                    continue;
                }
                items.add(new KnowledgeDocumentInfo(name, "builtin",
                        knowledgeBaseService.searchBySourceName(name).size(), 0L, true));
            }
            return items;
        }
    }

    public boolean isBuiltin(String sourceName) {
        synchronized (lock) {
            return builtinNames.contains(sourceName);
        }
    }

    public boolean isManual(String sourceName) {
        synchronized (lock) {
            return manualNames.contains(sourceName);
        }
    }

    private void loadFromDisk() {
        List<StoredDocument> documents = loadAll();
        for (StoredDocument doc : documents) {
            manualNames.add(doc.sourceName());
            manualDocuments.put(doc.sourceName(), doc);
            LOGGER.info("重新加载上传文档: {} (type={})", doc.sourceName(), doc.sourceType());
        }
        knowledgeBaseService.ingestAll(documents.stream()
                .map(doc -> new IngestRequest(doc.sourceType(), doc.sourceName(), doc.content()))
                .toList());
    }

    private List<StoredDocument> loadAll() {
        if (!Files.isDirectory(storageDir)) {
            return List.of();
        }
        List<StoredDocument> documents = new ArrayList<>();
        try (Stream<Path> files = Files.list(storageDir)) {
            files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> readDocument(path).ifPresent(documents::add));
        } catch (IOException e) {
            LOGGER.warn("扫描知识文档目录失败: {}", e.getMessage());
        }
        return documents;
    }

    private Optional<StoredDocument> readDocument(Path file) {
        try {
            return Optional.of(objectMapper.readValue(file.toFile(), StoredDocument.class));
        } catch (IOException e) {
            LOGGER.warn("读取知识文档失败: {} - {}", file.getFileName(), e.getMessage());
            return Optional.empty();
        }
    }

    private boolean persist(StoredDocument document) {
        Path target = storageDir.resolve(fileName(document.sourceName()));
        Path temporary = null;
        try {
            Files.createDirectories(storageDir);
            temporary = Files.createTempFile(storageDir, "doc-", ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), document);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            LOGGER.warn("持久化知识文档失败: {}", e.getMessage());
            return false;
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // 临时文件清理失败不影响已完成的持久化。
                }
            }
        }
    }

    /** 文件名用 sourceName 的哈希，规避非法字符与冲突 */
    private String fileName(String sourceName) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(sourceName.getBytes(StandardCharsets.UTF_8));
            return "doc_" + HexFormat.of().formatHex(hash, 0, 8) + ".json";
        } catch (Exception e) {
            return "doc_" + Math.abs(sourceName.hashCode()) + ".json";
        }
    }
}
