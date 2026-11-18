# btry-template

基于 btry 通用基座的最小新项目骨架（Xtry 模板）：登录/JWT、RBAC、租户隔离、支付（回调/查单/对账/退款）、对象存储、可观测性全部来自内核，只需写自己的垂直业务包。


> 现状：模板依赖本机安装的 btry 内核模块（btry-platform / btry-contracts / btry-identity / btry-payment），`mvn install` 后即可构建。

## 快速开始

1. 安装内核（在 btry 仓库根目录）：

   ```bash
   cd server && mvn -B -DskipTests install
   ```

2. 准备 MySQL / Redis（本机 127.0.0.1:3306 / 6379），模板首次启动会自动建库并跑内核迁移脚本。

3. 启动：

   ```bash
   mvn -B spring-boot:run
   ```

4. 验证内置能力：

   ```bash
   # 登录（内核账号体系，首次启动会打印初始密码）
   curl -X POST http://127.0.0.1:8090/api/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"username":"admin","password":"<初始密码>"}'

   # 自己的业务接口（权限码来自内核 RBAC）
   curl http://127.0.0.1:8090/api/demo/ping -H "Authorization: Bearer <token>"
   ```

## 示例垂直域：资料包（P4.5 验证）

`com.wherelee.template.resource` 演示一个新业务域如何只依赖内核契约完成交易闭环：

1. `GET /api/resource-packs`：资料包列表（权限码复用内核 RBAC）；
2. `POST /api/resource-packs/{id}/orders`：下单并调用内核 `PaymentOperations` 发起支付；
3. `POST /api/resource-packs/orders/{orderNo}/mock-pay`：仅 Mock 渠道的本地验证入口；
4. 支付成功后内核发件箱投递 `PAYMENT_SUCCEEDED`，模板的 `ResourcePackPaymentListener` 幂等发放权益；
5. `GET /api/resource-packs/entitlements/me`：查看已购权益。

对应集成测试：`ResourcePackFlowIntegrationTest`（登录 → 下单 → 支付 → 履约 → 权益）。

## 已复用的内核能力

| 能力 | 内核入口 |
|---|---|
| 登录 / JWT / 租户隔离 | btry-identity：`/api/auth/*`，`JwtAuthFilter`，`TenantContext` |
| RBAC 权限码 | `@RequirePermission`，`/api/admin/roles` |
| COS 直传 | `/api/teacher/videos/upload-url` 等 |
| 支付（Mock/支付宝） | `/api/orders/{orderNo}/pay`、`/api/payments/mock/*`、回调 `/api/payments/alipay/notify` |
| 事务性发件箱 / 对账 | `t_payment_outbox`、`/api/admin/recon/*` |
| 可观测性 | `/actuator/prometheus`、traceId 日志、告警 |

## 新增自己的垂直业务

1. 新建包 `com.wherelee.template.<domain>`；
2. Controller 用内核的 `@RequirePermission("<perm>")` 声明权限；
3. 新权限码在 `PermissionCodes` 中登记并给角色授权（运营端 API 即可，无需改鉴权代码）；
4. 数据访问走自己的表 + `JdbcTemplate`/MyBatis，跨模块调用只依赖 `btry-contracts`。

## 注意

- 模板与内核共享数据库迁移历史（Flyway）；新项目请用自己的库并评估迁移策略。
- 生产使用前替换 JWT secret、COS 密钥，并关闭 Mock 支付。
