package com.wherelee.edu.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.DependencyType;
import org.springframework.modulith.docs.Documenter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Modulith 模块边界门禁。
 *
 * <p>模块 = {@code com.wherelee.edu.modules} 的直接子包
 * （identity / course / learning / commerce / operations / platform）。</p>
 *
 * <p>本期（P4 第一阶段）发现三模块存在既有循环（commerce ↔ course ↔ learning，
 * 均由 {@code *.api} 契约造成）。架构暂不重构，采取"循环棘轮"策略：
 * 当前循环写入 {@code docs/generated/modulith-cycle-baseline.txt}，基线只允许下降
 * （消除循环必须同步改小基线；新增循环直接让构建变红）。</p>
 *
 * <p>模块图与模块文档由 {@link Documenter} 生成到 target/spring-modulith-docs，
 * 刷新快照命令见 docs/模块化内核与P4实施记录.md。</p>
 */
class ModulithModulesTest {

    private static final String BASE_PACKAGE = "com.wherelee.edu.modules";
    private static final Path CYCLE_BASELINE =
            Path.of("..", "docs", "generated", "modulith-cycle-baseline.txt");

    private final ApplicationModules modules = ApplicationModules.of(BASE_PACKAGE);

    @Test
    void moduleCyclesMatchBaseline() throws IOException {
        Map<String, Set<String>> graph = dependencyGraph();
        List<Set<String>> cycles = stronglyConnectedComponents(graph).stream()
                .filter(component -> component.size() > 1)
                .map(TreeSet::new)
                .collect(Collectors.toList());

        Set<Set<String>> actual = cycles.stream()
                .map(component -> (Set<String>) new LinkedHashSet<>(component))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Set<String>> expected = readBaseline();

        assertThat(actual)
                .as("模块循环依赖出现变化。新增循环 = 架构退化；循环已消除请同步改小基线 %s",
                        CYCLE_BASELINE.normalize())
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void moduleGraphCanBeRenderedToDocumentation() {
        new Documenter(modules).writeDocumentation();
        Path allDocs = Path.of("target", "spring-modulith-docs", "all-docs.adoc");
        assertThat(Files.exists(allDocs)).isTrue();
        assertThat(Files.exists(Path.of("target", "spring-modulith-docs", "components.puml"))).isTrue();
    }

    private Map<String, Set<String>> dependencyGraph() {
        Map<String, Set<String>> graph = new TreeMap<>();
        for (ApplicationModule module : modules) {
            String name = module.getIdentifier().toString();
            Set<String> targets = module.getDirectDependencies(modules,
                            DependencyType.USES_COMPONENT, DependencyType.ENTITY,
                            DependencyType.EVENT_LISTENER, DependencyType.DEFAULT)
                    .stream()
                    .map(dependency -> dependency.getTargetModule().getIdentifier().toString())
                    .filter(target -> !target.equals(name))
                    .collect(Collectors.toCollection(TreeSet::new));
            graph.put(name, targets);
        }
        return graph;
    }

    /** 简单可达性求强连通分量（模块数量个位数，Floyd 闭包足够）。 */
    private List<Set<String>> stronglyConnectedComponents(Map<String, Set<String>> graph) {
        List<String> nodes = new ArrayList<>(graph.keySet());
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            index.put(nodes.get(i), i);
        }
        int n = nodes.size();
        boolean[][] reach = new boolean[n][n];
        for (int i = 0; i < n; i++) {
            for (String target : graph.get(nodes.get(i))) {
                Integer targetIndex = index.get(target);
                if (targetIndex != null) {
                    reach[i][targetIndex] = true;
                }
            }
        }
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                if (!reach[i][k]) {
                    continue;
                }
                for (int j = 0; j < n; j++) {
                    reach[i][j] = reach[i][j] || reach[k][j];
                }
            }
        }
        boolean[] assigned = new boolean[n];
        List<Set<String>> components = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (assigned[i]) {
                continue;
            }
            Set<String> component = new TreeSet<>();
            for (int j = 0; j < n; j++) {
                if (reach[i][j] && reach[j][i]) {
                    component.add(nodes.get(j));
                    assigned[j] = true;
                }
            }
            components.add(component);
        }
        return components;
    }

    private Set<Set<String>> readBaseline() throws IOException {
        assertThat(Files.exists(CYCLE_BASELINE))
                .as("缺少模块循环基线文件：%s", CYCLE_BASELINE.normalize())
                .isTrue();
        Set<Set<String>> baseline = new LinkedHashSet<>();
        for (String line : Files.readAllLines(CYCLE_BASELINE)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            baseline.add(new TreeSet<>(List.of(trimmed.split("\\s*,\\s*"))));
        }
        return baseline;
    }
}
