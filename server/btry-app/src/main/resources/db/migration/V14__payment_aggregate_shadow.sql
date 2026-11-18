-- P1：支付单独立建模（影子阶段）
--
-- 目标：支付从「订单的附属表」升级为独立聚合，业务侧只通过 app_id + biz_type + biz_no 关联。
-- 本迁移只做新增，不改任何现有读路径：
--   1. 新建 t_payment_order / t_payment_outbox / t_recon_task / t_recon_diff / t_channel_bill；
--   2. 给 attempt/event/refund 增加 payment_no 引用（可空，影子期回填）；
--   3. 给 t_order 增加 payment_no（订单只保留支付单号，不再持有渠道细节）；
--   4. 用一次回填让历史数据与影子模型对齐，后续比对任务以此为基线。
--
-- 回滚：本轮不删除任何旧列，回滚只需 drop 新表 + drop 新列。

-- ---------------------------------------------------------------- 支付单

CREATE TABLE t_payment_order
(
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    payment_no        VARCHAR(40)  NOT NULL COMMENT '支付单号，对外唯一标识',
    app_id            VARCHAR(32)  NOT NULL COMMENT '接入方应用标识（多项目复用）',
    biz_type          VARCHAR(32)  NOT NULL COMMENT '业务类型，由业务侧定义，支付不解析',
    biz_no            VARCHAR(64)  NOT NULL COMMENT '业务单号，由业务侧生成',
    tenant_id         BIGINT       NOT NULL DEFAULT 0 COMMENT '租户维度，A 期预留',
    subject           VARCHAR(128) NOT NULL COMMENT '商品标题快照',
    amount_cents      BIGINT       NOT NULL,
    currency          VARCHAR(3)   NOT NULL DEFAULT 'CNY',
    status            VARCHAR(20)  NOT NULL COMMENT 'CREATED/PAYING/PAID/FAILED/CLOSED/REFUNDING/REFUNDED/REFUND_FAILED',
    channel           VARCHAR(20)  NULL,
    provider_trade_no VARCHAR(64)  NULL,
    notify_url        VARCHAR(255) NULL,
    metadata_json     VARCHAR(1024) NULL COMMENT '业务上下文透传，支付永不解析',
    source_updated_at DATETIME(3)  NOT NULL COMMENT '来源单据更新时间，用于防止影子写回退',
    expire_at         DATETIME(3)  NOT NULL,
    paid_at           DATETIME(3)  NULL,
    version           INT          NOT NULL DEFAULT 0,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_no (payment_no),
    UNIQUE KEY uk_payment_biz (app_id, biz_type, biz_no),
    UNIQUE KEY uk_payment_provider_trade (channel, provider_trade_no),
    KEY idx_payment_status_expire (status, expire_at),
    KEY idx_payment_status_updated (status, updated_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='支付单（独立聚合）';

-- ---------------------------------------------------------------- 事务性发件箱

CREATE TABLE t_payment_outbox
(
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    event_type   VARCHAR(40)   NOT NULL COMMENT 'PAYMENT_SUCCEEDED / PAYMENT_FAILED / REFUND_SUCCEEDED',
    aggregate_no VARCHAR(40)   NOT NULL COMMENT '支付单号',
    payload      VARCHAR(4000) NOT NULL,
    status       VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED',
    attempts     INT           NOT NULL DEFAULT 0,
    next_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_error   VARCHAR(500)  NULL,
    created_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    sent_at      DATETIME(3)   NULL,
    PRIMARY KEY (id),
    KEY idx_outbox_due (status, next_at),
    KEY idx_outbox_aggregate (aggregate_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='支付事件发件箱';

-- ---------------------------------------------------------------- 对账

CREATE TABLE t_recon_task
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    task_no      VARCHAR(40) NOT NULL,
    channel      VARCHAR(20) NOT NULL,
    bill_date    DATE        NOT NULL,
    status       VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/DONE/FAILED',
    total_count  INT         NOT NULL DEFAULT 0,
    match_count  INT         NOT NULL DEFAULT 0,
    diff_count   INT         NOT NULL DEFAULT 0,
    started_at   DATETIME(3) NULL,
    finished_at  DATETIME(3) NULL,
    created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_recon_task_no (task_no),
    UNIQUE KEY uk_recon_channel_date (channel, bill_date),
    KEY idx_recon_status (status, bill_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='对账任务';

CREATE TABLE t_recon_diff
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    task_no         VARCHAR(40)  NOT NULL,
    payment_no      VARCHAR(40)  NULL,
    biz_no          VARCHAR(64)  NULL,
    diff_type       VARCHAR(32)  NOT NULL COMMENT 'LOCAL_ONLY/PROVIDER_ONLY/AMOUNT/STATUS',
    expected_amount BIGINT       NULL,
    actual_amount   BIGINT       NULL,
    detail          VARCHAR(500) NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/HANDLED/IGNORED',
    handled_by      BIGINT       NULL,
    handled_at      DATETIME(3)  NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_diff_task (task_no, diff_type),
    KEY idx_diff_status (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='对账差异明细';

CREATE TABLE t_channel_bill
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    channel     VARCHAR(20)  NOT NULL,
    bill_date   DATE         NOT NULL,
    file_key    VARCHAR(255) NOT NULL,
    checksum    VARCHAR(64)  NULL,
    line_count  INT          NOT NULL DEFAULT 0,
    imported_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_bill_channel_date (channel, bill_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='渠道账单原始记录';

-- ---------------------------------------------------------------- 引用列

ALTER TABLE t_payment_attempt
    ADD COLUMN payment_no VARCHAR(40) NULL COMMENT '所属支付单号' AFTER order_id,
    ADD KEY idx_attempt_payment_no (payment_no);

ALTER TABLE t_payment_event
    ADD COLUMN payment_no VARCHAR(40) NULL COMMENT '所属支付单号' AFTER order_id,
    ADD KEY idx_event_payment_no (payment_no);

ALTER TABLE t_refund
    ADD COLUMN payment_no VARCHAR(40) NULL COMMENT '所属支付单号' AFTER order_id,
    ADD KEY idx_refund_payment_no (payment_no);

ALTER TABLE t_order
    ADD COLUMN payment_no VARCHAR(40) NULL COMMENT '支付单号，订单只保留引用不持有支付细节' AFTER status,
    ADD UNIQUE KEY uk_order_payment_no (payment_no);

-- ---------------------------------------------------------------- 历史数据回填
-- 影子期 payment_no 规则固定为 P + order_no，与 Java 侧 PaymentShadowNo 保持一致。

UPDATE t_order
SET payment_no = CONCAT('P', order_no)
WHERE payment_no IS NULL;

INSERT INTO t_payment_order
    (payment_no, app_id, biz_type, biz_no, tenant_id, subject, amount_cents, currency,
     status, channel, provider_trade_no, source_updated_at, expire_at, paid_at)
SELECT CONCAT('P', o.order_no),
       'btry',
       'COURSE_ORDER',
       o.order_no,
       0,
       o.series_title_snapshot,
       o.price_cents,
       o.currency,
       CASE o.status
           WHEN 'CREATED' THEN 'CREATED'
           WHEN 'PAYING' THEN 'PAYING'
           WHEN 'PAY_FAILED' THEN 'FAILED'
           WHEN 'PAID' THEN 'PAID'
           WHEN 'FULFILLED' THEN 'PAID'
           WHEN 'REFUNDING' THEN 'REFUNDING'
           WHEN 'REFUNDED' THEN 'REFUNDED'
           WHEN 'REFUND_FAILED' THEN 'REFUND_FAILED'
           ELSE 'CLOSED'
           END,
       o.pay_channel,
       o.provider_trade_no,
       o.updated_at,
       o.expire_at,
       o.paid_at
FROM t_order o
WHERE o.payment_no IS NOT NULL
ON DUPLICATE KEY UPDATE t_payment_order.source_updated_at = t_payment_order.source_updated_at;

UPDATE t_payment_attempt a
    JOIN t_order o ON o.id = a.order_id
SET a.payment_no = o.payment_no
WHERE a.payment_no IS NULL;

UPDATE t_payment_event e
    JOIN t_order o ON o.id = e.order_id
SET e.payment_no = o.payment_no
WHERE e.payment_no IS NULL;

UPDATE t_refund r
    JOIN t_order o ON o.id = r.order_id
SET r.payment_no = o.payment_no
WHERE r.payment_no IS NULL;
