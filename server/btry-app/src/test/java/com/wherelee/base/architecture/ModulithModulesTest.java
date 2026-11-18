package com.wherelee.base.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Modulith 模块边界硬门禁。
 *
 * <p>模块 = {@code com.wherelee.base.modules} 的直接子包（contracts / identity / payment /
 * platform）。</p>
 *
 * <p>`modules.verify()` 检查：模块图无循环、跨模块依赖已在
 * `@ApplicationModule(allowedDependencies)` 中声明、模块内部类型未被非法暴露。
 * 任一违规都会让 `mvn test` 与 CI 变红。</p>
 */
class ModulithModulesTest {

    private static final String BASE_PACKAGE = "com.wherelee.base.modules";

    private final ApplicationModules modules = ApplicationModules.of(BASE_PACKAGE);

    @Test
    void moduleBoundariesAreIntact() {
        modules.verify();
    }

    @Test
    void moduleGraphCanBeRenderedToDocumentation() {
        new Documenter(modules).writeDocumentation();
        Path docs = Path.of("target", "spring-modulith-docs");
        assertThat(Files.exists(Path.of("target", "spring-modulith-docs", "all-docs.adoc"))).isTrue();
        assertThat(Files.exists(Path.of("target", "spring-modulith-docs", "components.puml"))).isTrue();
    }
}
