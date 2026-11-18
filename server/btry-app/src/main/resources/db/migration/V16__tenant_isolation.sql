-- P9：多租户地基
--
-- 1. 所有业务表增加 tenant_id（默认 0 = 当前单租户部署，历史数据自动归入）；
-- 2. 安全相关的唯一键改为「租户前缀」，允许不同租户复用同一用户名/单号；
-- 3. 加租户维度索引，避免隔离查询全表扫。
--
-- 隔离策略：应用层 TenantContext + SQL 拦截器强制注入（见 platform/tenant）。
-- 回滚：删除新列/恢复旧唯一键，tenant_id 全 0 时等价于原结构。

ALTER TABLE t_user
    ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 0 AFTER id,
    DROP INDEX uk_user_username,
    ADD UNIQUE KEY uk_user_tenant_username (tenant_id, username),
    ADD KEY idx_user_tenant_status (tenant_id, status);

ALTER TABLE t_teacher_invitation
    ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 0 AFTER id,
    ADD KEY idx_invitation_tenant_status (tenant_id, status);

ALTER TABLE t_series
    ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 0 AFTER id,
    ADD KEY idx_series_tenant_status (tenant_id, status);

ALTER TABLE t_series_chapter
    ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 0 AFTER id,
    ADD KEY idx_chapter_tenant (tenant_id);

ALTER TABLE t_series_video
    ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 0 AFTER id,
    ADD KEY idx_video_tenant (tenant_id);

ALTER TABLE t_student_series
    ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 0 AFTER id,
    ADD KEY idx_entitlement_tenant_user (tenant_id, user_id);

ALTER TABLE t_learning_progress
    ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 0 AFTER id,
    ADD KEY idx_progress_tenant_user (tenant_id, user_id);

ALTER TABLE t_playback_log
    ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 0 AFTER id,
    ADD KEY idx_playback_tenant_created (tenant_id, created_at);

ALTER TABLE t_order
    ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 0 AFTER id,
    DROP INDEX uk_order_no,
    DROP INDEX uk_order_client_request,
    ADD UNIQUE KEY uk_order_tenant_no (tenant_id, order_no),
    ADD UNIQUE KEY uk_order_tenant_client (tenant_id, user_id, client_request_id),
    ADD KEY idx_order_tenant_status (tenant_id, status);

ALTER TABLE t_payment_attempt
    ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 0 AFTER id,
    ADD KEY idx_attempt_tenant (tenant_id);

ALTER TABLE t_payment_event
    ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 0 AFTER id,
    ADD KEY idx_event_tenant (tenant_id);

ALTER TABLE t_refund
    ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 0 AFTER id,
    ADD KEY idx_refund_tenant (tenant_id);

ALTER TABLE t_alert_log
    ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 0 AFTER id,
    ADD KEY idx_alert_tenant_created (tenant_id, created_at);

ALTER TABLE t_admin_audit_log
    ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 0 AFTER id,
    ADD KEY idx_audit_tenant_created (tenant_id, created_at);

ALTER TABLE t_payment_outbox
    ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 0 AFTER id,
    ADD KEY idx_outbox_tenant (tenant_id);
