# 表访问清单（自动生成）

- 生成脚本：`tools/table_access_inventory.py`
- 表数量：16；有代码访问的表：17

## 1. 汇总

| 表 | 归属建议 | 访问模块数 | 访问类数 | 跨表 SQL | 写入方 | 读取方 |
|---|---|---|---|---|---|---|
| `t_admin_audit_log` | platform/audit | 1 | 1 | 0 | AdminAuditRepository | — |
| `t_alert_log` | platform/alert | 1 | 1 | 0 | AlertLogRepository | — |
| `t_channel_bill` | payment | 1 | 1 | 0 | ReconRepository | — |
| `t_payment_attempt` | payment | 1 | 2 | 0 | PaymentRepository | MockChannelBillProvider, PaymentRepository |
| `t_payment_event` | payment | 1 | 1 | 0 | PaymentRepository | PaymentRepository |
| `t_payment_order` | payment | 1 | 2 | 0 | PaymentOrderRepository | PaymentOrderRepository, ReconRepository |
| `t_payment_outbox` | payment | 1 | 1 | 0 | PaymentOutboxRepository | PaymentOutboxRepository |
| `t_permission` | identity | 1 | 1 | 2 | — | RbacRepository |
| `t_recon_diff` | payment | 1 | 1 | 0 | ReconRepository | ReconRepository |
| `t_recon_task` | payment | 1 | 1 | 0 | ReconRepository | — |
| `t_refund` | payment | 1 | 1 | 0 | PaymentRepository | PaymentRepository |
| ⚠️ `t_role` | identity | 2 | 2 | 2 | RbacRepository | KernelDataInitializer, RbacRepository |
| `t_role_permission` | identity | 1 | 1 | 2 | RbacRepository | RbacRepository |
| ⚠️ `t_user` | identity | 2 | 5 | 3 | KernelDataInitializer, UserRepository | AccountDirectoryService, AccountQueryService, KernelDataInitializer, UserInvitationMapper, UserRepository |
| `t_user_invitation` | identity | 1 | 1 | 1 | UserInvitationMapper | UserInvitationMapper |
| ⚠️ `t_user_role` | identity | 2 | 2 | 4 | RbacRepository | RbacRepository |

## 2. 跨模块访问（需要收口的表）

### `t_role` — 2 个模块访问

- **bootstrap**：`KernelDataInitializer` (READ, server/btry-app/src/main/java/com/wherelee/edu/bootstrap/KernelDataInitializer.java)
- **identity**：`RbacRepository` (READ+WRITE, server/btry-identity/src/main/java/com/wherelee/edu/modules/identity/rbac/RbacRepository.java)

### `t_user` — 2 个模块访问

- **bootstrap**：`KernelDataInitializer` (READ+WRITE, server/btry-app/src/main/java/com/wherelee/edu/bootstrap/KernelDataInitializer.java)
- **identity**：`AccountDirectoryService` (READ, server/btry-identity/src/main/java/com/wherelee/edu/modules/identity/user/AccountDirectoryService.java)；`AccountQueryService` (READ, server/btry-identity/src/main/java/com/wherelee/edu/modules/identity/user/AccountQueryService.java)；`UserInvitationMapper` (READ, server/btry-identity/src/main/resources/mapper/UserInvitationMapper.xml)；`UserRepository` (READ+WRITE, server/btry-identity/src/main/java/com/wherelee/edu/modules/identity/user/UserRepository.java)

### `t_user_role` — 2 个模块访问

- **bootstrap**：`KernelDataInitializer` (OTHER, server/btry-app/src/main/java/com/wherelee/edu/bootstrap/KernelDataInitializer.java)
- **identity**：`RbacRepository` (OTHER+READ+WRITE, server/btry-identity/src/main/java/com/wherelee/edu/modules/identity/rbac/RbacRepository.java)


## 3. 多表 SQL（跨域查询候选）

| 模块 | 类 | 涉及表 | 文件 |
|---|---|---|---|
| bootstrap | `KernelDataInitializer` | `t_role` / `t_user` / `t_user_role` | server/btry-app/src/main/java/com/wherelee/edu/bootstrap/KernelDataInitializer.java |
| identity | `RbacRepository` | `t_permission` / `t_role` / `t_role_permission` / `t_user_role` | server/btry-identity/src/main/java/com/wherelee/edu/modules/identity/rbac/RbacRepository.java |
| identity | `UserInvitationMapper` | `t_user` / `t_user_invitation` | server/btry-identity/src/main/resources/mapper/UserInvitationMapper.xml |

## 4. 未被代码访问的表

_无。_
