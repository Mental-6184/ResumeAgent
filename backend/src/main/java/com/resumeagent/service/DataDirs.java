package com.resumeagent.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 数据目录解析：统一「IDE 从项目根启动」与「mvn 从 backend/ 启动」两种工作目录，
 * 使档案/知识库/复盘数据始终读写同一份数据（backend/data）。
 *
 * 解析规则：优先使用已有 profiles 数据的目录；若 ./backend/data 有数据而 ./data 没有，
 * 则采用 ./backend/data，并把另一处零散数据文件迁移合并进来，避免"换种方式启动数据就消失"。
 */
public final class DataDirs {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataDirs.class);
    private static final List<String> SUB_DIRS = List.of("profiles", "knowledge", "reviews", "jobs");
    private static volatile Path resolved;

    private DataDirs() {
    }

    public static synchronized Path resolve() {
        if (resolved == null) {
            resolved = resolveFor(Path.of("").toAbsolutePath().normalize());
        }
        return resolved;
    }

    /** 基于指定工作目录解析（包私有，便于单元测试模拟不同启动位置） */
    static synchronized Path resolveFor(Path cwd) {
        Path direct = cwd.resolve("data");
        Path nested = cwd.resolve("backend").resolve("data");

        Path chosen = hasProfileData(nested) && !hasProfileData(direct) ? nested : direct;
        // 另一处的零散数据迁移合并进来（幂等：目标已存在则跳过）
        migrate(chosen.equals(nested) ? direct : nested, chosen);
        LOGGER.info("数据目录: {}", chosen);
        return chosen;
    }

    private static boolean hasProfileData(Path root) {
        return Files.isDirectory(root.resolve("profiles"));
    }

    private static void migrate(Path from, Path to) {
        for (String sub : SUB_DIRS) {
            Path sourceDir = from.resolve(sub);
            if (!Files.isDirectory(sourceDir)) {
                continue;
            }
            Path targetDir = to.resolve(sub);
            try {
                Files.createDirectories(targetDir);
                try (Stream<Path> files = Files.list(sourceDir)) {
                    for (Path file : files.toList()) {
                        if (!Files.isRegularFile(file)) {
                            continue;
                        }
                        Path target = targetDir.resolve(file.getFileName().toString());
                        if (!Files.exists(target)) {
                            Files.move(file, target);
                            LOGGER.info("迁移数据文件: {} -> {}", file, target);
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("数据迁移失败（可忽略）: {}", e.getMessage());
            }
        }
    }
}
