package com.wherelee.edu.modules.commerce.payment.shadow;

import com.wherelee.edu.modules.platform.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * t_payment_order 的影子写与比对查询。
 *
 * <p>写入必须是幂等且单调的：用 {@code source_updated_at} 做守卫，
 * 迟到的同步不会把状态写回退（ORDER BY 旧值覆盖新值是最容易出的线上事故）。</p>
 */
@Repository
public class PaymentOrderShadowRepository {

    private static final RowMapper<PaymentOrderShadowRow> ROW_MAPPER = (rs, rowNum) -> new PaymentOrderShadowRow(
            rs.getString("payment_no"),
            rs.getString("app_id"),
            rs.getString("biz_type"),
            rs.getString("biz_no"),
            rs.getLong("tenant_id"),
            rs.getString("subject"),
            rs.getLong("amount_cents"),
            rs.getString("currency"),
            rs.getString("status"),
            rs.getString("channel"),
            rs.getString("provider_trade_no"),
            rs.getString("metadata_json"),
            rs.getTimestamp("source_updated_at").toLocalDateTime(),
            rs.getTimestamp("expire_at").toLocalDateTime(),
            toLocal(rs.getTimestamp("paid_at"))
    );

    private static final RowMapper<ShadowPair> PAIR_MAPPER = (rs, rowNum) -> new ShadowPair(
            rs.getString("order_no"),
            rs.getString("order_status"),
            rs.getLong("order_amount_cents"),
            rs.getString("order_channel"),
            rs.getString("order_trade_no"),
            rs.getString("attempt_channel"),
            rs.getString("attempt_trade_no"),
            rs.getString("payment_no"),
            rs.getString("pay_status"),
            rs.getObject("pay_amount_cents") == null ? null : rs.getLong("pay_amount_cents"),
            rs.getString("pay_channel"),
            rs.getString("pay_trade_no")
    );

    private final JdbcTemplate jdbc;

    public PaymentOrderShadowRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 幂等 upsert。source_updated_at 是守卫列，必须最后赋值——MySQL 的 ON DUPLICATE KEY UPDATE
     * 按从左到右求值，先改守卫列会让后续 IF 判断读到新值，守卫失效。
     */
    public int upsert(PaymentOrderShadowRow row) {
        return jdbc.update("""
                INSERT INTO t_payment_order
                    (payment_no, app_id, biz_type, biz_no, tenant_id, subject, amount_cents, currency,
                     status, channel, provider_trade_no, metadata_json,
                     expire_at, paid_at, source_updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    subject = IF(VALUES(source_updated_at) >= source_updated_at, VALUES(subject), subject),
                    amount_cents = IF(VALUES(source_updated_at) >= source_updated_at,
                                      VALUES(amount_cents), amount_cents),
                    currency = IF(VALUES(source_updated_at) >= source_updated_at, VALUES(currency), currency),
                    expire_at = IF(VALUES(source_updated_at) >= source_updated_at,
                                   VALUES(expire_at), expire_at),
                    status = IF(VALUES(source_updated_at) >= source_updated_at, VALUES(status), status),
                    channel = IF(VALUES(source_updated_at) >= source_updated_at, VALUES(channel), channel),
                    provider_trade_no = IF(VALUES(source_updated_at) >= source_updated_at,
                                           VALUES(provider_trade_no), provider_trade_no),
                    metadata_json = IF(VALUES(source_updated_at) >= source_updated_at,
                                       VALUES(metadata_json), metadata_json),
                    paid_at = IF(VALUES(source_updated_at) >= source_updated_at, VALUES(paid_at), paid_at),
                    version = IF(VALUES(source_updated_at) >= source_updated_at, version + 1, version),
                    source_updated_at = IF(VALUES(source_updated_at) >= source_updated_at,
                                           VALUES(source_updated_at), source_updated_at)
                """,
                row.paymentNo(), row.appId(), row.bizType(), row.bizNo(), row.tenantId(),
                row.subject(), row.amountCents(), row.currency(), row.status(), row.channel(),
                row.providerTradeNo(), row.metadataJson(),
                Timestamp.valueOf(row.expireAt()), toTimestamp(row.paidAt()),
                Timestamp.valueOf(row.sourceUpdatedAt()));
    }

    /**
     * 把历史/新增的 attempt、event、refund 行补上 payment_no 引用。
     *
     * <p>刻意**不更新 t_order**：订单的 payment_no 在下单时已写入，
     * 若在这里每次状态变化都 UPDATE t_order，会给资金链路引入额外的行锁争用
     * （实测会让并发退款更容易撞死锁）。</p>
     */
    public int backfillReferences(Long orderId, String orderNo, String paymentNo) {
        int changed = 0;
        changed += jdbc.update(
                "UPDATE t_payment_attempt SET payment_no = ? WHERE order_id = ? AND payment_no IS NULL",
                paymentNo, orderId);
        changed += jdbc.update(
                "UPDATE t_payment_event SET payment_no = ? WHERE order_id = ? AND payment_no IS NULL",
                paymentNo, orderId);
        changed += jdbc.update(
                "UPDATE t_refund SET payment_no = ? WHERE order_id = ? AND payment_no IS NULL",
                paymentNo, orderId);
        return changed;
    }

    /** 取最近 N 笔订单与支付单的对照行（含缺失行）。 */
    public List<ShadowPair> findPairs(int limit) {
        return jdbc.query("""
                SELECT o.order_no,
                       o.status              AS order_status,
                       o.price_cents         AS order_amount_cents,
                       o.pay_channel         AS order_channel,
                       o.provider_trade_no   AS order_trade_no,
                       (SELECT a.channel FROM t_payment_attempt a
                        WHERE a.order_id = o.id ORDER BY a.attempt_no DESC LIMIT 1) AS attempt_channel,
                       (SELECT a.provider_trade_no FROM t_payment_attempt a
                        WHERE a.order_id = o.id ORDER BY a.attempt_no DESC LIMIT 1) AS attempt_trade_no,
                       p.payment_no          AS payment_no,
                       p.status              AS pay_status,
                       p.amount_cents        AS pay_amount_cents,
                       p.channel             AS pay_channel,
                       p.provider_trade_no   AS pay_trade_no
                FROM t_order o
                LEFT JOIN t_payment_order p ON p.payment_no = CONCAT('P', o.order_no)
                WHERE o.tenant_id = ?
                ORDER BY o.id DESC
                LIMIT ?
                """, PAIR_MAPPER, TenantContext.current(), limit);
    }

    /** 统计未回填 payment_no 的引用行（影子覆盖度指标）。 */
    public ReferenceOrphans countOrphans() {
        return new ReferenceOrphans(
                countNull("t_payment_attempt"),
                countNull("t_payment_event"),
                countNull("t_refund"),
                countNull("t_order"));
    }

    private int countNull(String table) {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE payment_no IS NULL", Integer.class);
        return value == null ? 0 : value;
    }

    private static Timestamp toTimestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static LocalDateTime toLocal(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    /** 订单与支付单的对照行；paymentNo 为 null 表示影子缺失。 */
    public record ShadowPair(
            String orderNo,
            String orderStatus,
            long orderAmountCents,
            String orderChannel,
            String orderTradeNo,
            String attemptChannel,
            String attemptTradeNo,
            String paymentNo,
            String payStatus,
            Long payAmountCents,
            String payChannel,
            String payTradeNo
    ) {
    }

    /** 未回填 payment_no 的行数。 */
    public record ReferenceOrphans(int attempts, int events, int refunds, int orders) {
        public int total() {
            return attempts + events + refunds + orders;
        }
    }
}
