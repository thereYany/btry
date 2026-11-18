-- P2：支付反转准备
--
-- 反转后支付不再以「订单 id」为主键关联：t_payment_attempt / t_payment_event / t_refund
-- 通过 payment_no 关联支付单，历史 order_id 列保留但允许为空（兼容期双列并存）。
-- 这只是一次可回滚的结构放宽，不删除任何列。

ALTER TABLE t_payment_attempt
    MODIFY COLUMN order_id BIGINT NULL COMMENT '历史字段；反转后以 payment_no 关联支付单';

ALTER TABLE t_payment_event
    MODIFY COLUMN order_id BIGINT NULL COMMENT '历史字段；反转后以 payment_no 关联支付单';

ALTER TABLE t_refund
    MODIFY COLUMN order_id BIGINT NULL COMMENT '历史字段；反转后以 payment_no 关联支付单';
