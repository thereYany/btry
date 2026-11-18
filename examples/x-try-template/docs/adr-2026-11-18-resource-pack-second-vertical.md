# ADR：用「资料包」垂直域验证内核复用（P4.5）

- 日期：2026-11-18
- 状态：已完成
- 关联：btry `PaymentOperations` 通用支付契约、`examples/x-try-template` 模板

## 背景

P4 完成后 btry 已具备 contracts 模块、Maven 多模块（platform/contracts/app）与 platform starter，
但"新项目能否 1 天接进来"只是断言，没有第二个消费者证据。支付域此前只有原业务订单入口，
新垂直域无法复用支付。

## 决策

用最小真实垂直域「资料包」验证：

1. 契约先行：在 `contracts.commerce` 增加与业务无关的 `PaymentOperations`（按 `bizType/bizNo` 幂等发起、查询）；
2. 支付域实现 `PaymentFacade`，历史业务单也改走该入口（传固定 paymentNo 兼容历史单号）；
3. 模板仓新增资料包表/接口/事件监听器，支付成功事件由模板自己的 `PaymentSettledListener` 消费并发放授权；
4. 用集成测试跑通全链路，并量化接入成本。

## 结果（量化）

| 指标 | 数值 |
|---|---|
| 新增业务代码 | 2 个业务 Java 文件（Controller/Listener）+ 1 个集成测试 + 1 个迁移 SQL |
| 新增 LOC（含测试/配置） | 约 350 行（`git show --stat`） |
| 实际耗时 | 约 1.5 小时（含踩坑：Boot 4 测试包路径、COS/JWT/Payment 配置缺失、可执行 jar 不能当依赖） |
| 对 btry 内核的改动 | 1 个新契约（`PaymentOperations`）+ 1 个门面实现 + 订单入口重构 + 事件按 bizType 隔离 |
| btry 侧回归范围 | 单元 137 + 集成 41 + Modulith/ArchUnit/数据边界门禁（全部通过） |
| 模板侧验证 | 集成测试 1 个，覆盖 登录(JWT) → 资料包列表 → 下单 → 支付 → 授权发放（并入 examples 后已补 integration 测试 profile、修复角色种子与租户门禁适配，本仓集成测试通过） |

## 结论

- 内核复用成立：新垂直域不碰支付/订单/身份实现，只依赖 `contracts` + platform starter；
- 暴露的真实缺口已修复：支付需要业务无关入口、订单侧事件消费者要按 `bizType` 隔离、可执行 jar 不能作为库依赖；
- 后续接入成本主要在"配置项补齐"（JWT/COS/Payment 配置），可在 starter 里提供默认值或配置模板进一步降低。

## 后续

1. 把 btry 产物发布到私有 Maven 仓库，模板改为单行依赖；
2. 若出现第三个消费者，把 identity/payment 也抽成 starter；
3. 真实支付宝接入后，新垂直域支付无需任何代码变化。
