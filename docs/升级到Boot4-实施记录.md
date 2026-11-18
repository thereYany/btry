# 升级到 Spring Boot 4.1 实施记录

> 日期：2026-11-18　分支：`upgrade/boot-4.1`　基线 tag：`v0.9-a-p2`
> 结论：Boot 3.3.5（OSS 已于 2025-06-30 结束）→ **Boot 4.1.2 + Java 21**，全部测试与门禁通过，应用启动健康检查 UP。

## 1. 版本对照

| 组件 | 升级前 | 升级后 | 说明 |
|---|---|---|---|
| Spring Boot | 3.3.5（EOL） | **4.1.2** | 当前稳定版，OSS 支持至 2027-07-31 |
| Spring Framework | 6.x | 7.0.10 | Boot 4 自带 |
| Java | 17 | **21** | 本机默认已是 21；Boot 4 支持 17–26 |
| Spring Security（仅 crypto） | 6.x | 7.1.2 | 项目未用 starter-security，影响小 |
| MyBatis Spring Boot | 3.0.3 | **4.1.0** | Boot 4 对应版本 |
| Flyway | 10.x | 12.4.0 | Boot 4 BOM 管理 |
| Resilience4j | 2.2.0（spring-boot3） | 2.3.0（spring-boot3） | Boot 4 下自动配置仍可用（上下文加载验证） |
| Jackson | 2.x（Boot 管理） | **Jackson 3**（代码迁移）+ 2.21.7（仅 jjwt-jackson 传递依赖） | 见问题 4 |
| Micrometer Tracing | 1.3.x | 1.7.2 | 需补 Boot 4 的 tracing 自动配置模块 |
| OpenTelemetry exporter | 1.4x | 1.62.0 | Boot 4 BOM 管理 |
| ArchUnit | 1.3.0 | 1.5.1 | 支持 Java 21 字节码 |
| MySQL Connector/J | 8.x | 9.7.0 | Boot 4 BOM 管理 |

## 2. 问题清单与修复（按出现顺序）

| # | 现象 | 根因 | 修复 |
|---|---|---|---|
| 1 | POM 校验失败：`spring-boot-starter-aop` 版本缺失 | Boot 4 移除该 starter | 改为 `spring-boot-starter-aspectj` |
| 2 | `sqlSessionFactory or sqlSessionTemplate are required` | MyBatis starter 3.0.4 的自动配置不兼容 Boot 4 | `mybatis-spring-boot-starter` → 4.1.0 |
| 3 | `No qualifying bean of type io.micrometer.tracing.Tracer` | Boot 4 把可观测性自动配置模块化，不再随 actuator 提供 | 新增 `spring-boot-micrometer-tracing-opentelemetry` |
| 4 | 编译失败：`com.fasterxml.jackson` 不存在 / `JsonProcessingException` 未捕获 | Boot 4 默认 Jackson 3（`tools.jackson`），异常改为非受检 | 4 个主代码 + 3 个测试迁移到 Jackson 3：`ObjectMapper` 改包、`JsonProcessingException` → `JacksonException`、`new ObjectMapper()` → `JsonMapper.builder().build()` |
| 5 | 单测编译失败：`NoResourceFoundException` 构造参数不匹配 | Spring 7 构造器新增参数 | 测试补第三个参数 |
| 6 | 单元/集成都通过，但 `java -jar` 启动后**迁移不执行**，首个查询报 `Unknown column 'tenant_id'` | Boot 4 的 Flyway 自动配置拆到独立模块，`flyway-core` 不再触发 | 新增 `spring-boot-flyway`（**冒烟测试才发现，单测/集成因测试库已迁移而漏过**） |

## 3. 验证证据

| 项 | 结果 |
|---|---|
| Java / Boot | `java.version=21`，parent `4.1.2` |
| 单元测试 | **131 通过 / 0 失败** |
| 集成测试 | **37 通过 / 0 失败**（含支付回调/查单/对账/退款/多租户） |
| 架构规则 | 9 条 ArchUnit 全绿（含 R5：payment 不得依赖业务模块） |
| 数据边界门禁 | 跨模块表 0、多表 SQL 4 类（未上升） |
| 启动冒烟 | `java -jar` + `/actuator/health` → HTTP 200，db/liveness/readiness/diskSpace 全 UP |
| Flyway 冒烟 | 克隆库上从 V13 自动迁移到 **V17**（V14–V17 全部成功） |
| CI | `.github/workflows/ci.yml` Java 17 → 21 |

冒烟方法：用 `mysqldump` 把 `btry_edu` 克隆到独立库 `btry_upgrade`（过滤 `DEFINER`），在克隆库上启动应用，避免污染开发库；验证后进程已停止。

## 4. 回退方案

- 代码回退：`git checkout main`（main 仍是 `v0.9-a-p2` 基线直到合并），或 `git reset --hard v0.9-a-p2`。
- 依赖回退：Boot 3.3.5 只需还原 `server/pom.xml` 与 Jackson import；数据库迁移 V14–V17 为前向兼容（只加列/表，不删不改），回退 Boot 版本不影响已迁移的表结构。
- 风险提示：不建议长期停在 3.3/3.5（均已过 OSS 支持期）。

## 5. 遗留注意

1. **jjwt-jackson 仍带 Jackson 2.21.7**：仅 JWT 序列化使用，Jackson 2 仍在维护，不构成 EOL 风险；若想彻底单栈可换 `jjwt-gson`。
2. **resilience4j-spring-boot3** 在 Boot 4 下可工作，但项目名仍标注 boot3；若后续出 boot4 模块再切换。
3. 本次升级**未做顺手重构**，只解决兼容性，保证可回退。
