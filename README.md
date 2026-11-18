# btry · 通用业务基座

btry 是一个**与业务无关的 Java 业务基座**（Java 21 / Spring Boot 4.1.2 / MySQL / Redis）：把登录鉴权、账号与邀请、RBAC、支付、退款、对账、租户隔离、审计、告警、对象存储这些"每个业务都会重复造一遍"的能力做成可复用模块。

**网课业务不在这里**：录播课程平台（课程商店/学习空间/订单/权益/教师端/运营端）已迁到独立仓库 **ctry** —— <https://github.com/thereYany/ctry>。btry 只提供内核能力与一个最小的内核演示宿主（`btry-app`）。

## 模块

| 模块 | 说明 |
|---|---|
| `btry-platform` | 共享内核 starter：租户 SQL 拦截、事务/幂等/限流/审计 AOP、告警、对象存储 + 签名 URL 缓存、Tracing、Resilience4j 降级、统一异常/分页、健康检查 |
| `btry-contracts` | 跨模块稳定契约：`contracts.identity.*`（账号/角色/邀请只读与写操作）、`contracts.payment.*`（发起支付/退款/对账/告警查询） |
| `btry-identity` | 账号、登录/JWT、修改密码、登录限流、RBAC（角色/权限码）、通用用户邀请（roleCode） |
| `btry-payment` | 支付单/支付尝试/回调事件/事务性发件箱/退款重试/渠道对账；Mock 与支付宝渠道适配；`PaymentSettledEvent` 对外事件 |
| `btry-app` | 内核演示宿主（仅 14 个类）：启动类、内核种子（admin）、运营端账号/角色/对账接口、支付告警巡检 |

依赖方向：`app → identity/payment → platform/contracts`；`platform` 不依赖任何业务；支付不认识订单/课程，只认 `bizType + bizNo`，业务侧实现 `PaymentSettledListener` 自行履约。

## 快速开始

### 前置

- JDK 21、Maven 3.9+
- MySQL 8、Redis 6+

### 构建与启动

```bash
cd server
mvn -B -DskipTests install          # 安装内核模块到本地 Maven 仓库（垂直应用依赖它）

DB_URL='jdbc:mysql://127.0.0.1:3306/btry_core?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=true' \
DB_USER=root DB_PASSWORD=root \
mvn -B -pl btry-app spring-boot:run
```

启动后后端在 `http://127.0.0.1:8084`，健康检查 `/actuator/health`，Prometheus 指标在管理端口 `9091/actuator/prometheus`。
首次启动会创建 `admin` 账号（`SEED_ADMIN_PASSWORD` 指定初始密码；留空则随机生成并在启动日志打印一次，首次登录强制改密）。

核心接口：`POST /api/auth/login`、`/api/auth/me`、`/api/admin/users`、`/api/admin/roles`、`/api/admin/invitations`、`/api/admin/recon/*`、`/api/payments/mock/*`（内核 Mock 支付辅助接口）。

## 测试

```bash
# 单元测试（不含 integration tag）
mvn -B test

# 集成测试：真实 MySQL + Redis
IT_DB_URL='jdbc:mysql://127.0.0.1:3306/btry_it?allowPublicKeyRetrieval=true&useSSL=false&allowMultiQueries=true' \
IT_DB_USER=btry IT_DB_PASSWORD=btrypass \
IT_REDIS_HOST=127.0.0.1 IT_REDIS_PORT=6379 IT_REDIS_DATABASE=15 \
COS_SECRET_ID=dummy COS_SECRET_KEY=dummy \
mvn -B -Pintegration test
```

另有架构门禁（ArchUnit）：内核不得依赖任何垂直业务、`platform` 不得反向依赖、Modulith 模块校验等，见 `btry-app/src/test/java/com/wherelee/edu/architecture/`。

## 数据边界门禁

从仓库根目录执行（跨模块表访问、多表 SQL 类、单表最多访问模块数只能降不能升）：

```bash
python3 tools/table_access_inventory.py --baseline docs/generated/table-access-baseline.json
```

## 数据库与 Flyway

- 内核表由各模块 jar 自带迁移：`db/migration/platform`（V3xx）、`db/migration/identity`（V1xx）、`db/migration/payment`（V2xx）。
- 垂直应用在自己的 Flyway `locations` 里追加自己的迁移目录（ctry 使用 V9xx 段、独立库 `btry_ctry`）。
- 内核表：`t_user`、`t_user_invitation`、`t_role`、`t_permission`、`t_role_permission`、`t_user_role`、`t_payment_*`、`t_recon_*`、`t_channel_bill`、`t_refund`、`t_alert_log`、`t_admin_audit_log`。

## 目录

```text
server/   多模块后端（platform / contracts / identity / payment / app）
docs/     设计与运行文档（含内核与垂直拆分方案）
tools/    数据边界门禁、冒烟与压测脚本
deploy/   历史部署脚本（课程平台时期，保留备查）
```

## 文档

- [内核与垂直拆分方案（解耦 V2）](docs/内核与垂直拆分方案.md)
- [模块解耦设计计划（V1）](docs/模块解耦设计计划.md)
- [数据所有权与分库边界](docs/数据所有权与分库边界.md)
- [Redis 降级与 MQ 边界](docs/Redis降级与MQ边界.md)
- [CI 流水线与仓库镜像](docs/CI流水线与仓库镜像.md)

## 非目标

- 不内置任何垂直业务（课程/电商/会员……）；垂直业务在各自仓库实现并依赖本基座。
- 不做自助注册（账号由管理员创建或邀请激活，角色码由垂直应用定义）。
- 不引入 2PC/XA，跨域一致性用「本地事务 + 发件箱 + 幂等消费 + 对账」。

## CI

[![ci](https://github.com/thereYany/btry/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/thereYany/btry/actions/workflows/ci.yml)（数据边界门禁 + 单元测试 + 集成测试）

## License

个人项目，暂未指定开源许可证；如需复用请联系作者。
