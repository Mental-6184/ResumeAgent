package com.resumeagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 数据目录解析单测：IDE（项目根）与 mvn（backend/）两种启动位置共享同一份数据，
 * 零散数据自动迁移合并。
 */
class DataDirsTest {

    @TempDir
    Path cwd;

    @Test
    void ideWorkingDirShouldShareBackendData() throws Exception {
        // 模拟：backend/data/profiles 有数据（mvn 启动写入过），项目根 data 有零散上传文档（IDE 启动写入过）
        Path nested = cwd.resolve("backend").resolve("data");
        Files.createDirectories(nested.resolve("profiles"));
        Files.writeString(nested.resolve("profiles").resolve("t.json"), "{}", StandardCharsets.UTF_8);
        Path direct = cwd.resolve("data");
        Files.createDirectories(direct.resolve("knowledge"));
        Files.writeString(direct.resolve("knowledge").resolve("doc.json"), "{}", StandardCharsets.UTF_8);

        Path resolved = DataDirs.resolveFor(cwd);

        assertThat(resolved).isEqualTo(nested);
        // 零散文档被迁移合并进统一目录
        assertThat(nested.resolve("knowledge").resolve("doc.json")).exists();
    }

    @Test
    void mvnWorkingDirShouldUseBackendDataDirectly() throws Exception {
        // 模拟：cwd 就是 backend/，data/profiles 就在 cwd/data
        Path direct = cwd.resolve("data");
        Files.createDirectories(direct.resolve("profiles"));
        Files.writeString(direct.resolve("profiles").resolve("t.json"), "{}", StandardCharsets.UTF_8);

        Path resolved = DataDirs.resolveFor(cwd);

        assertThat(resolved).isEqualTo(direct);
    }

    @Test
    void freshWorkingDirShouldDefaultToLocalData() {
        Path resolved = DataDirs.resolveFor(cwd);

        assertThat(resolved).isEqualTo(cwd.resolve("data"));
    }
}
