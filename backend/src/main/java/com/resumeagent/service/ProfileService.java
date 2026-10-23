package com.resumeagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeagent.dto.ProfileListResponse;
import com.resumeagent.dto.ProfileSaveRequest;
import com.resumeagent.dto.ProfileTemplateResponse;
import com.resumeagent.dto.ProfileVersionItem;
import com.resumeagent.model.ProfileForm;
import com.resumeagent.model.ProfileTemplate;
import com.resumeagent.dto.IngestRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 用户档案模板服务：网申表单 + 简历文本的多模板管理（增删改查、激活切换）。
 *
 * 持久化：每个模板一个 JSON 文件（data/profiles/{id}.json），激活状态存 active.txt，
 * 重启后自动加载并同步进知识库。
 *
 * 知识库联动：激活的模板内容以 {@link KnowledgeBaseService#PROFILE_SOURCE_NAME}
 * 为 sourceName 入库，切换模板时整体替换——所有 RAG 流程（对话/Agent 分析）
 * 因此始终以用户亲手填写/上传的信息为第一事实来源。
 */
@Service
public class ProfileService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileService.class);
    private static final String ACTIVE_FILE = "active.txt";

    private final KnowledgeBaseService knowledgeBaseService;
    private final ObjectMapper objectMapper;
    private final Path storageDir;
    private final Object lock = new Object();

    /** id → 模板（写操作在 lock 内进行） */
    private final Map<String, ProfileTemplate> templates = new LinkedHashMap<>();
    private volatile String activeId;

    /**
     * 档案代数：激活档案内容每变化一次 +1。
     * ChatService 用它检测档案切换，丢弃携带旧身份的对话历史。
     */
    private final java.util.concurrent.atomic.AtomicLong generation = new java.util.concurrent.atomic.AtomicLong();
    private String syncedFingerprint = "";

    @org.springframework.beans.factory.annotation.Autowired
    public ProfileService(KnowledgeBaseService knowledgeBaseService, ObjectMapper objectMapper) {
        this(knowledgeBaseService, objectMapper, DataDirs.resolve().resolve("profiles"));
    }

    public ProfileService(KnowledgeBaseService knowledgeBaseService, ObjectMapper objectMapper, Path storageDir) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.objectMapper = objectMapper;
        this.storageDir = storageDir;
        loadFromDisk();
        syncKnowledgeBase();
    }

    public ProfileListResponse list() {
        synchronized (lock) {
            return new ProfileListResponse(templates.values().stream().map(this::toResponse).toList(), activeId);
        }
    }

    public Optional<ProfileTemplate> active() {
        synchronized (lock) {
            return activeId == null ? Optional.empty() : Optional.ofNullable(templates.get(activeId));
        }
    }

    public Optional<ProfileTemplate> get(String id) {
        synchronized (lock) {
            return Optional.ofNullable(templates.get(id));
        }
    }

    public ProfileTemplate create(ProfileSaveRequest request) {
        long now = System.currentTimeMillis();
        ProfileTemplate template = new ProfileTemplate(
                UUID.randomUUID().toString(),
                request.name().trim(),
                safeForm(request.form()),
                safeText(request.resumeText()),
                safeText(request.resumeFileName()),
                now, now);
        synchronized (lock) {
            templates.put(template.id(), template);
            persist(template);
        }
        syncKnowledgeBase();
        return template;
    }

    public Optional<ProfileTemplate> update(String id, ProfileSaveRequest request) {
        ProfileTemplate updated;
        synchronized (lock) {
            ProfileTemplate existing = templates.get(id);
            if (existing == null) {
                return Optional.empty();
            }
            // 更新前把旧内容快照进版本历史（回滚操作因此是双向的）
            snapshotVersion(existing);
            updated = new ProfileTemplate(
                    id,
                    request.name().trim(),
                    safeForm(request.form()),
                    safeText(request.resumeText()),
                    safeText(request.resumeFileName()),
                    existing.createdAt(),
                    System.currentTimeMillis());
            templates.put(id, updated);
            persist(updated);
        }
        syncKnowledgeBase();
        return Optional.of(updated);
    }

    /** 删除模板；若删除的是激活模板，则清空激活状态 */
    public boolean delete(String id) {
        boolean removed;
        synchronized (lock) {
            removed = templates.remove(id) != null;
            if (!removed) {
                return false;
            }
            if (id.equals(activeId)) {
                activeId = null;
                persistActive();
            }
            try {
                Files.deleteIfExists(storageDir.resolve(id + ".json"));
                Path versionsDir = versionsDir(id);
                if (Files.isDirectory(versionsDir)) {
                    try (Stream<Path> files = Files.list(versionsDir)) {
                        for (Path file : files.toList()) {
                            Files.deleteIfExists(file);
                        }
                    }
                    Files.deleteIfExists(versionsDir);
                }
            } catch (IOException e) {
                LOGGER.warn("删除模板文件失败: {}", e.getMessage());
            }
        }
        syncKnowledgeBase();
        return true;
    }

    public Optional<ProfileTemplate> activate(String id) {
        synchronized (lock) {
            ProfileTemplate template = templates.get(id);
            if (template == null) {
                return Optional.empty();
            }
            activeId = id;
            persistActive();
            syncKnowledgeBase();
            return Optional.of(template);
        }
    }

    /** 当前档案代数；激活档案内容变化时递增 */
    public long generation() {
        return generation.get();
    }

    // ===== 版本历史 =====

    /** 模板版本保留上限（每个模板） */
    private static final int MAX_VERSIONS = 20;

    private Path versionsDir(String id) {
        return storageDir.resolve("versions").resolve(id);
    }

    /** 将模板旧内容快照到版本目录，并淘汰超出上限的最旧版本 */
    private void snapshotVersion(ProfileTemplate template) {
        try {
            Path dir = versionsDir(template.id());
            Files.createDirectories(dir);
            long timestamp = System.currentTimeMillis();
            Path file = dir.resolve(timestamp + ".json");
            // 同毫秒多次快照时顺延时间戳，避免互相覆盖
            while (Files.exists(file)) {
                timestamp++;
                file = dir.resolve(timestamp + ".json");
            }
            writeAtomically(file, template);
            pruneVersions(dir);
        } catch (IOException e) {
            LOGGER.warn("快照模板版本失败: {}", e.getMessage());
        }
    }

    private void pruneVersions(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> sorted = files
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
                    .toList();
            for (int i = MAX_VERSIONS; i < sorted.size(); i++) {
                Files.deleteIfExists(sorted.get(i));
            }
        } catch (IOException e) {
            LOGGER.warn("清理版本目录失败: {}", e.getMessage());
        }
    }

    /** 模板的版本历史列表（最新在前） */
    public List<ProfileVersionItem> listVersions(String id) {
        Path dir = versionsDir(id);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            List<ProfileVersionItem> items = new ArrayList<>();
            files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
                    .forEach(path -> readVersion(path)
                            .ifPresent(template -> items.add(new ProfileVersionItem(
                                    parseTimestamp(path),
                                    template.name(),
                                    versionSummary(template)))));
            return items;
        } catch (IOException e) {
            LOGGER.warn("读取版本列表失败: {}", e.getMessage());
            return List.of();
        }
    }

    public Optional<ProfileTemplate> getVersion(String id, long timestamp) {
        Path file = versionsDir(id).resolve(timestamp + ".json");
        return Files.exists(file) ? readVersion(file) : Optional.empty();
    }

    /**
     * 回滚：把指定历史版本写回为当前内容。
     * 当前内容在 update() 中自动入版本历史，因此回滚本身也可以再回滚。
     */
    public Optional<ProfileTemplate> rollback(String id, long timestamp) {
        Optional<ProfileTemplate> version = getVersion(id, timestamp);
        if (version.isEmpty()) {
            return Optional.empty();
        }
        ProfileTemplate template = version.get();
        return update(id, new ProfileSaveRequest(
                template.name(), template.form(), template.resumeText(), template.resumeFileName()));
    }

    private Optional<ProfileTemplate> readVersion(Path file) {
        try {
            return Optional.of(objectMapper.readValue(file.toFile(), ProfileTemplate.class));
        } catch (IOException e) {
            LOGGER.warn("读取版本失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private long parseTimestamp(Path file) {
        String name = file.getFileName().toString();
        try {
            return Long.parseLong(name.substring(0, name.length() - ".json".length()));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String versionSummary(ProfileTemplate template) {
        ProfileForm form = template.form();
        String who = form == null ? "" : safeText(form.name());
        String target = form == null ? "" : safeText(form.targetPosition());
        String summary = (who + (target.isEmpty() ? "" : " · " + target)).trim();
        return summary.isEmpty() ? safeText(template.resumeFileName()) : summary;
    }

    /** 激活模板内容整体替换进知识库（sourceName 固定为「我的档案」） */
    private void syncKnowledgeBase() {
        String fingerprint = active()
                .map(template -> template.id() + ":" + template.toKnowledgeText().hashCode())
                .orElse("");
        if (fingerprint.equals(syncedFingerprint)) {
            return;
        }
        syncedFingerprint = fingerprint;
        generation.incrementAndGet();

        knowledgeBaseService.removeBySourceName(KnowledgeBaseService.PROFILE_SOURCE_NAME);
        Optional<ProfileTemplate> active = active();
        if (active.isEmpty() || !active.get().hasContent()) {
            return;
        }
        knowledgeBaseService.ingest(new IngestRequest(
                "resume",
                KnowledgeBaseService.PROFILE_SOURCE_NAME,
                active.get().toKnowledgeText()));
    }

    private ProfileTemplateResponse toResponse(ProfileTemplate template) {
        return new ProfileTemplateResponse(
                template.id(), template.name(), template.form(), template.resumeText(),
                template.resumeFileName(), template.createdAt(), template.updatedAt(),
                template.id().equals(activeId));
    }

    private ProfileForm safeForm(ProfileForm form) {
        return form == null ? ProfileForm.empty() : form;
    }

    private String safeText(String text) {
        return text == null ? "" : text.trim();
    }

    private void persist(ProfileTemplate template) {
        try {
            Files.createDirectories(storageDir);
            writeAtomically(storageDir.resolve(template.id() + ".json"), template);
        } catch (IOException e) {
            LOGGER.warn("持久化模板失败: {}", e.getMessage());
        }
    }

    private void persistActive() {
        try {
            Files.createDirectories(storageDir);
            Path target = storageDir.resolve(ACTIVE_FILE);
            Path temporary = Files.createTempFile(storageDir, "active-", ".tmp");
            Files.writeString(temporary, activeId == null ? "" : activeId, StandardCharsets.UTF_8);
            moveAtomically(temporary, target);
        } catch (IOException e) {
            LOGGER.warn("持久化激活状态失败: {}", e.getMessage());
        }
    }

    private void writeAtomically(Path target, Object value) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), "profile-", ".tmp");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), value);
            moveAtomically(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void moveAtomically(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void loadFromDisk() {
        if (!Files.isDirectory(storageDir)) {
            return;
        }
        try (Stream<Path> files = Files.list(storageDir)) {
            files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(this::loadTemplate);
        } catch (IOException e) {
            LOGGER.warn("扫描模板目录失败: {}", e.getMessage());
        }
        try {
            Path activeFile = storageDir.resolve(ACTIVE_FILE);
            if (Files.exists(activeFile)) {
                String id = Files.readString(activeFile, StandardCharsets.UTF_8).trim();
                if (templates.containsKey(id)) {
                    activeId = id;
                }
            }
        } catch (IOException e) {
            LOGGER.warn("读取激活状态失败: {}", e.getMessage());
        }
    }

    private void loadTemplate(Path file) {
        try {
            ProfileTemplate template = objectMapper.readValue(file.toFile(), ProfileTemplate.class);
            templates.put(template.id(), template);
        } catch (IOException e) {
            LOGGER.warn("加载模板失败: {} - {}", file.getFileName(), e.getMessage());
        }
    }

    /** 供测试/管理用：当前模板 id 列表 */
    public List<String> templateIds() {
        synchronized (lock) {
            return new ArrayList<>(templates.keySet());
        }
    }
}
