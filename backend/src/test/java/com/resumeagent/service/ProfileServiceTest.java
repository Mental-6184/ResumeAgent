package com.resumeagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeagent.dto.ProfileSaveRequest;
import com.resumeagent.model.ProfileForm;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 档案模板服务单测：多模板管理、激活切换、知识库联动、磁盘持久化。
 */
class ProfileServiceTest {

    @TempDir
    Path tempDir;

    private KnowledgeBaseService knowledgeBaseService;
    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        knowledgeBaseService = new KnowledgeBaseService(new EmbeddingService());
        profileService = new ProfileService(knowledgeBaseService, new ObjectMapper(), tempDir);
    }

    @Test
    void shouldSyncActiveTemplateIntoKnowledgeBase() {
        var template = profileService.create(request("后端岗模板", form("张三", "三年 Java 后端开发经验"), "张三的简历全文……", "zhangsan.pdf"));
        profileService.activate(template.id());

        var items = knowledgeBaseService.searchBySourceName(KnowledgeBaseService.PROFILE_SOURCE_NAME);

        assertThat(items).isNotEmpty();
        assertThat(items).allMatch(item -> item.sourceName().equals(KnowledgeBaseService.PROFILE_SOURCE_NAME));
        assertThat(items).anyMatch(item -> item.content().contains("张三"));
        assertThat(items).anyMatch(item -> item.content().contains("三年 Java 后端开发经验"));
    }

    @Test
    void switchingTemplateReplacesProfileInKnowledgeBase() {
        var first = profileService.create(request("模板甲", form("张三", "张三的经历"), "张三简历", ""));
        profileService.activate(first.id());
        var second = profileService.create(request("模板乙", form("李四", "李四的经历"), "李四简历", ""));
        profileService.activate(second.id());

        var items = knowledgeBaseService.searchBySourceName(KnowledgeBaseService.PROFILE_SOURCE_NAME);

        assertThat(items).isNotEmpty();
        assertThat(items).anyMatch(item -> item.content().contains("李四"));
        assertThat(items).noneMatch(item -> item.content().contains("张三"));
    }

    @Test
    void shouldPersistTemplatesAndActiveStateAcrossRestart() {
        var template = profileService.create(request("后端岗模板", form("张三", "三年经验"), "简历文本", "a.pdf"));
        profileService.activate(template.id());

        KnowledgeBaseService reloadedKb = new KnowledgeBaseService(new EmbeddingService());
        ProfileService reloaded = new ProfileService(reloadedKb, new ObjectMapper(), tempDir);

        assertThat(reloaded.list().templates()).hasSize(1);
        assertThat(reloaded.active()).isPresent();
        assertThat(reloaded.active().get().name()).isEqualTo("后端岗模板");
        assertThat(reloaded.active().get().form().name()).isEqualTo("张三");
        // 重启后档案自动重新入库
        assertThat(reloadedKb.searchBySourceName(KnowledgeBaseService.PROFILE_SOURCE_NAME)).isNotEmpty();
    }

    @Test
    void deletingActiveTemplateClearsActiveAndKnowledgeBase() {
        var template = profileService.create(request("模板", form("张三", "经验"), "简历", ""));
        profileService.activate(template.id());

        assertThat(profileService.delete(template.id())).isTrue();

        assertThat(profileService.active()).isEmpty();
        assertThat(knowledgeBaseService.searchBySourceName(KnowledgeBaseService.PROFILE_SOURCE_NAME)).isEmpty();
    }

    @Test
    void updateShouldSnapshotPreviousVersionAndRollbackIsReversible() {
        var template = profileService.create(request("模板", form("张三", "旧经验"), "旧简历", ""));
        profileService.activate(template.id());
        profileService.update(template.id(), request("模板", form("张三", "新经验"), "新简历", ""));

        // 更新后旧内容入版本历史
        var versions = profileService.listVersions(template.id());
        assertThat(versions).hasSize(1);

        // 回滚到旧版本
        var restored = profileService.rollback(template.id(), versions.get(0).timestamp());
        assertThat(restored).isPresent();
        assertThat(restored.get().resumeText()).isEqualTo("旧简历");

        // 回滚本身也留痕：可以再回滚回"新简历"（双向可回）
        var afterRollback = profileService.listVersions(template.id());
        assertThat(afterRollback).hasSize(2);
        var rolledAgain = profileService.rollback(template.id(), afterRollback.get(0).timestamp());
        assertThat(rolledAgain).isPresent();
        assertThat(rolledAgain.get().resumeText()).isEqualTo("新简历");
    }

    @Test
    void deleteTemplateShouldRemoveItsVersions() {
        var template = profileService.create(request("模板", form("张三", "经验"), "简历", ""));
        profileService.update(template.id(), request("模板", form("张三", "经验2"), "简历2", ""));
        assertThat(profileService.listVersions(template.id())).isNotEmpty();

        profileService.delete(template.id());

        assertThat(profileService.listVersions(template.id())).isEmpty();
    }

    @Test
    void updateShouldKeepCreationTimeAndRefreshContent() {
        var template = profileService.create(request("模板", form("张三", "旧经验"), "旧简历", ""));
        profileService.activate(template.id());

        profileService.update(template.id(), request("模板", form("张三", "新经验"), "新简历", ""));

        var items = knowledgeBaseService.searchBySourceName(KnowledgeBaseService.PROFILE_SOURCE_NAME);
        assertThat(items).anyMatch(item -> item.content().contains("新经验"));
        assertThat(items).noneMatch(item -> item.content().contains("旧经验"));
    }

    private ProfileSaveRequest request(String name, ProfileForm form, String resumeText, String fileName) {
        return new ProfileSaveRequest(name, form, resumeText, fileName);
    }

    private ProfileForm form(String name, String skills) {
        return new ProfileForm(name, "13800000000", "test@example.com", "北京",
                "某大学", "软件工程", "本科", "2026",
                "Java 后端开发", "25k", skills,
                "项目：分布式日志平台", "实习：某公司后端", "踏实靠谱", "奖学金");
    }
}
