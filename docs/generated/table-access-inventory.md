# 表访问清单（自动生成）

- 生成脚本：`tools/table_access_inventory.py`
- 表数量：23；有代码访问的表：23

## 1. 汇总

| 表 | 归属建议 | 访问模块数 | 访问类数 | 跨表 SQL | 写入方 | 读取方 |
|---|---|---|---|---|---|---|
| `t_admin_audit_log` | operations/audit | 1 | 1 | 0 | AdminAuditRepository | — |
| `t_alert_log` | operations/alert | 1 | 1 | 0 | AlertLogRepository | — |
| `t_channel_bill` | commerce | 1 | 1 | 0 | ReconRepository | — |
| `t_learning_progress` | learning/progress | 1 | 2 | 0 | LearningProgressRepository | ProgressQueryService |
| `t_order` | commerce/order | 1 | 2 | 0 | OrderRepository | OrderRepository, PaymentRepository |
| `t_payment_attempt` | commerce/payment | 1 | 2 | 0 | PaymentRepository | MockChannelBillProvider, PaymentRepository |
| `t_payment_event` | commerce/payment | 1 | 1 | 0 | PaymentRepository | PaymentRepository |
| `t_payment_order` | commerce | 1 | 2 | 0 | PaymentOrderRepository | PaymentOrderRepository, ReconRepository |
| `t_payment_outbox` | commerce | 1 | 1 | 0 | PaymentOutboxRepository | PaymentOutboxRepository |
| `t_permission` | identity | 1 | 1 | 2 | — | RbacRepository |
| `t_playback_log` | learning/playback | 1 | 3 | 0 | PlaybackLogMapper, PlaybackLogRepository | PlaybackLogMapper, PlaybackStatsQueryService |
| `t_recon_diff` | commerce | 1 | 1 | 0 | ReconRepository | ReconRepository |
| `t_recon_task` | commerce | 1 | 1 | 0 | ReconRepository | — |
| `t_refund` | commerce/payment | 1 | 1 | 0 | PaymentRepository | PaymentRepository |
| `t_role` | identity | 1 | 1 | 1 | RbacRepository | RbacRepository |
| `t_role_permission` | identity | 1 | 1 | 2 | RbacRepository | RbacRepository |
| ⚠️ `t_series` | course/catalog | 2 | 4 | 2 | AdminSeriesMapper, DataInitializer, TeacherContentRepository | AdminSeriesMapper, CatalogQueryService, DataInitializer, TeacherContentRepository |
| ⚠️ `t_series_chapter` | course/catalog | 2 | 4 | 2 | DataInitializer, TeacherContentRepository | AdminSeriesMapper, CatalogQueryService, DataInitializer, TeacherContentRepository |
| ⚠️ `t_series_video` | course/catalog | 2 | 4 | 2 | DataInitializer, TeacherContentRepository | AdminSeriesMapper, CatalogQueryService, TeacherContentRepository |
| ⚠️ `t_student_series` | commerce/entitlement | 2 | 2 | 0 | EntitlementRepository | EntitlementRepository |
| `t_teacher_invitation` | identity | 1 | 1 | 1 | TeacherInvitationMapper | TeacherInvitationMapper |
| ⚠️ `t_user` | identity | 2 | 5 | 1 | DataInitializer, UserRepository | AccountDirectoryService, AccountQueryService, DataInitializer, TeacherInvitationMapper, UserRepository |
| `t_user_role` | identity | 1 | 1 | 3 | RbacRepository | RbacRepository |

## 2. 跨模块访问（需要收口的表）

### `t_series` — 2 个模块访问

- **bootstrap**：`DataInitializer` (READ+WRITE, server/src/main/java/com/wherelee/edu/bootstrap/DataInitializer.java)
- **course**：`AdminSeriesMapper` (READ+WRITE, server/src/main/resources/mapper/AdminSeriesMapper.xml)；`CatalogQueryService` (READ, server/src/main/java/com/wherelee/edu/modules/course/catalog/CatalogQueryService.java)；`TeacherContentRepository` (READ+WRITE, server/src/main/java/com/wherelee/edu/modules/course/teacher/TeacherContentRepository.java)

### `t_series_chapter` — 2 个模块访问

- **bootstrap**：`DataInitializer` (READ+WRITE, server/src/main/java/com/wherelee/edu/bootstrap/DataInitializer.java)
- **course**：`AdminSeriesMapper` (READ, server/src/main/resources/mapper/AdminSeriesMapper.xml)；`CatalogQueryService` (READ, server/src/main/java/com/wherelee/edu/modules/course/catalog/CatalogQueryService.java)；`TeacherContentRepository` (READ+WRITE, server/src/main/java/com/wherelee/edu/modules/course/teacher/TeacherContentRepository.java)

### `t_series_video` — 2 个模块访问

- **bootstrap**：`DataInitializer` (WRITE, server/src/main/java/com/wherelee/edu/bootstrap/DataInitializer.java)
- **course**：`AdminSeriesMapper` (READ, server/src/main/resources/mapper/AdminSeriesMapper.xml)；`CatalogQueryService` (READ, server/src/main/java/com/wherelee/edu/modules/course/catalog/CatalogQueryService.java)；`TeacherContentRepository` (READ+WRITE, server/src/main/java/com/wherelee/edu/modules/course/teacher/TeacherContentRepository.java)

### `t_student_series` — 2 个模块访问

- **bootstrap**：`DataInitializer` (OTHER, server/src/main/java/com/wherelee/edu/bootstrap/DataInitializer.java)
- **commerce**：`EntitlementRepository` (READ+WRITE, server/src/main/java/com/wherelee/edu/modules/commerce/entitlement/EntitlementRepository.java)

### `t_user` — 2 个模块访问

- **bootstrap**：`DataInitializer` (READ+WRITE, server/src/main/java/com/wherelee/edu/bootstrap/DataInitializer.java)
- **identity**：`AccountDirectoryService` (READ, server/src/main/java/com/wherelee/edu/modules/identity/user/AccountDirectoryService.java)；`AccountQueryService` (READ, server/src/main/java/com/wherelee/edu/modules/identity/user/AccountQueryService.java)；`TeacherInvitationMapper` (READ, server/src/main/resources/mapper/TeacherInvitationMapper.xml)；`UserRepository` (READ+WRITE, server/src/main/java/com/wherelee/edu/modules/identity/user/UserRepository.java)


## 3. 多表 SQL（跨域查询候选）

| 模块 | 类 | 涉及表 | 文件 |
|---|---|---|---|
| course | `AdminSeriesMapper` | `t_series` / `t_series_chapter` / `t_series_video` | server/src/main/resources/mapper/AdminSeriesMapper.xml |
| course | `CatalogQueryService` | `t_series` / `t_series_chapter` / `t_series_video` | server/src/main/java/com/wherelee/edu/modules/course/catalog/CatalogQueryService.java |
| course | `TeacherContentRepository` | `t_series` / `t_series_chapter` / `t_series_video` | server/src/main/java/com/wherelee/edu/modules/course/teacher/TeacherContentRepository.java |
| identity | `RbacRepository` | `t_permission` / `t_role` / `t_role_permission` / `t_user_role` | server/src/main/java/com/wherelee/edu/modules/identity/rbac/RbacRepository.java |
| identity | `TeacherInvitationMapper` | `t_teacher_invitation` / `t_user` | server/src/main/resources/mapper/TeacherInvitationMapper.xml |

## 4. 未被代码访问的表

_无。_
