package com.wherelee.edu.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内核纯洁性门禁：btry 的代码/迁移/配置里不允许出现任何垂直业务词汇。
 *
 * <p>这是「垂直业务彻底拆出通用基座」的可执行证据——不靠 code review 记忆，
 * 而是每次构建都扫一遍 server 下所有模块源码。历史文档（docs/）不受此限制。</p>
 */
class KernelPurityTest {

    private static final Pattern VERTICAL_WORDS = Pattern.compile(
            "\\b(series|video|chapter|course|learning|playback|teacher)s?\\b",
            Pattern.CASE_INSENSITIVE);

    private static final List<String> SCANNED_SUFFIXES = List.of(
            ".java", ".xml", ".sql", ".yml", ".yaml", ".properties", ".imports", ".factories");

    @Test
    void kernelSourcesContainNoVerticalBusinessVocabulary() throws IOException {
        Path serverDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().getParent();
        assertThat(serverDir).isNotNull();
        List<String> violations = new ArrayList<>();
        try (Stream<Path> modules = Files.list(serverDir)) {
            for (Path module : modules.filter(Files::isDirectory).toList()) {
                Path src = module.resolve("src/main");
                if (!Files.isDirectory(src)) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(src)) {
                    for (Path file : files.filter(Files::isRegularFile).toList()) {
                        if (SCANNED_SUFFIXES.stream().noneMatch(file.toString()::endsWith)) {
                            continue;
                        }
                        String text = Files.readString(file);
                        Matcher matcher = VERTICAL_WORDS.matcher(text);
                        if (matcher.find()) {
                            violations.add(serverDir.relativize(file) + " -> " + matcher.group());
                        }
                    }
                }
            }
        }
        assertThat(violations)
                .as("通用基座代码里不允许出现垂直业务词汇")
                .isEmpty();
    }

    @Test
    void kernelMigrationsOnlyCreateKernelTables() throws IOException {
        Path serverDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().getParent();
        Pattern table = Pattern.compile("create\\s+table\\s+`?(t_[a-z_]+)`?", Pattern.CASE_INSENSITIVE);
        List<String> tables = new ArrayList<>();
        try (Stream<Path> modules = Files.list(serverDir)) {
            for (Path module : modules.filter(Files::isDirectory).toList()) {
                Path migrations = module.resolve("src/main/resources/db/migration");
                if (!Files.isDirectory(migrations)) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(migrations)) {
                    for (Path file : files.filter(p -> p.toString().endsWith(".sql")).toList()) {
                        Matcher matcher = table.matcher(Files.readString(file));
                        while (matcher.find()) {
                            tables.add(matcher.group(1).toLowerCase(Locale.ROOT));
                        }
                    }
                }
            }
        }
        assertThat(tables).isNotEmpty();
        assertThat(tables).allSatisfy(name -> assertThat(name).startsWith("t_"));
        assertThat(tables).noneMatch(name -> name.contains("series")
                || name.contains("video") || name.contains("chapter")
                || name.contains("course") || name.contains("learning")
                || name.contains("playback") || name.contains("teacher"));
    }
}
