package com.wherelee.edu.modules.commerce.payment.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wherelee.edu.modules.commerce.payment.api.PaymentSettledEvent;
import com.wherelee.edu.modules.platform.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 事务性发件箱：与支付状态变更同事务写入，投递器异步发出。
 *
 * <p>为什么必须 outbox：回调里"改库 + 发消息"是双写，进程在两步之间崩溃就会丢事件。
 * 同事务写 outbox 后，事件与支付状态同生共死，投递失败只是延迟，不会丢。</p>
 */
@Repository
public class PaymentOutboxRepository {

    /** 所有租户一起扫描（投递器是平台级 worker，不是某个租户的请求）。 */
    private static final String ALL_TENANTS = "tenant_id >= 0";

    private static final RowMapper<OutboxRow> MAPPER = (rs, rowNum) -> new OutboxRow(
            rs.getLong("id"),
            rs.getString("event_type"),
            rs.getString("aggregate_no"),
            rs.getString("payload"),
            rs.getInt("attempts")
    );

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PaymentOutboxRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** 在调用方事务内追加一条待投递事件。 */
    public void append(PaymentSettledEvent event) {
        try {
            jdbc.update("""
                    INSERT INTO t_payment_outbox (event_type, aggregate_no, payload, status, next_at)
                    VALUES (?, ?, ?, 'PENDING', CURRENT_TIMESTAMP(3))
                    """, event.eventType(), event.paymentNo(),
                    objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("支付事件序列化失败 paymentNo=" + event.paymentNo(), e);
        }
    }

    /** 抢占一批到期事件：先改 SENDING 再返回，避免多实例/重入重复投递。 */
    public List<OutboxRow> claimDue(int limit) {
        List<OutboxRow> rows = jdbc.query("""
                SELECT id, event_type, aggregate_no, payload, attempts
                FROM t_payment_outbox
                WHERE tenant_id >= 0 AND status = 'PENDING' AND next_at <= CURRENT_TIMESTAMP(3)
                ORDER BY id
                LIMIT ?
                """, MAPPER, limit);
        return rows.stream().filter(row -> jdbc.update("""
                UPDATE t_payment_outbox
                SET status = 'SENDING'
                WHERE id = ? AND status = 'PENDING' AND tenant_id >= 0
                """, row.id()) == 1).toList();
    }

    public PaymentSettledEvent payload(OutboxRow row) {
        try {
            return objectMapper.readValue(row.payload(), PaymentSettledEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("支付事件反序列化失败 id=" + row.id(), e);
        }
    }

    public void markSent(Long id) {
        jdbc.update("""
                UPDATE t_payment_outbox
                SET status = 'SENT', sent_at = CURRENT_TIMESTAMP(3),
                    attempts = attempts + 1, last_error = NULL
                WHERE id = ?
                """, id);
    }

    public void reschedule(Long id, int attempts, int backoffSeconds, String error) {
        jdbc.update("""
                UPDATE t_payment_outbox
                SET status = 'PENDING', attempts = ?, last_error = ?,
                    next_at = TIMESTAMPADD(SECOND, ?, CURRENT_TIMESTAMP(3))
                WHERE id = ?
                """, attempts, truncate(error), backoffSeconds, id);
    }

    public void markDead(Long id, String error) {
        jdbc.update("""
                UPDATE t_payment_outbox
                SET status = 'DEAD', last_error = ?, attempts = attempts + 1
                WHERE id = ?
                """, truncate(error), id);
    }

    /** 积压在途（PENDING + SENDING）数量，用于指标与告警。 */
    public int countInFlight() {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_payment_outbox WHERE tenant_id >= 0 AND status IN ('PENDING','SENDING')",
                Integer.class);
        return value == null ? 0 : value;
    }

    public int countDead() {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_payment_outbox WHERE tenant_id >= 0 AND status = 'DEAD'",
                Integer.class);
        return value == null ? 0 : value;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    /** 发件箱一行。 */
    public record OutboxRow(Long id, String eventType, String aggregateNo, String payload, int attempts) {
    }
}
