-- P2：对账查询索引
--
-- 对账按「渠道 + 支付完成日期」扫我方支付单，加复合索引避免全表扫描。
-- 只加索引，不改数据。

ALTER TABLE t_payment_order
    ADD KEY idx_payment_channel_paid (channel, paid_at);
