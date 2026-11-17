package com.wherelee.edu.modules.commerce.integration;

import com.wherelee.edu.modules.commerce.payment.shadow.PaymentOrderShadowRepository;
import com.wherelee.edu.modules.commerce.payment.shadow.PaymentOrderShadowRow;
import com.wherelee.edu.modules.commerce.payment.shadow.PaymentOrderShadowService;
import com.wherelee.edu.modules.commerce.payment.shadow.PaymentOrderShadowService.ShadowComparison;
import com.wherelee.edu.modules.commerce.payment.shadow.PaymentShadowNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A 期证据测试：支付单独立模型（影子）必须与订单事实保持一致。
 *
 * <p>覆盖四件事：影子随订单状态推进、引用列回填、比对能发现差异、迟到同步不会让状态回退。</p>
 */
class PaymentShadowIntegrationTest extends CommerceIntegrationTestSupport {

    @Test
    @DisplayName("支付单影子模型随下单→支付→履约推进，且全量比对零差异")
    void shadowFollowsOrderThroughFullLifecycle() {
        String orderNo = createOrder(nextRequestId()).orderNo();
        String paymentNo = PaymentShadowNo.of(orderNo);

        assertThat(shadowStatus(paymentNo)).isEqualTo("CREATED");
        assertClean();

        pay(orderNo);
        assertThat(shadowStatus(paymentNo)).isEqualTo("PAYING");
        assertClean();

        paymentService.acceptVerified(successNotification(orderNo));
        assertThat(shadowStatus(paymentNo)).isEqualTo("PAID");
        // 履约完成后支付单仍是 PAID：支付只表达「钱」，履约是业务语义
        assertThat(orderRepository.findByOrderNo(orderNo).orElseThrow().status().name()).isEqualTo("FULFILLED");
        assertThat(shadowStatus(paymentNo)).isEqualTo("PAID");

        ShadowComparison comparison = paymentShadowService.compare(100);
        assertThat(comparison.differences()).as("diffs=%s", comparison.differences()).isEmpty();
        assertThat(comparison.orphans().total()).as("orphans=%s", comparison.orphans()).isZero();
        assertThat(comparison.clean()).isTrue();

        // 业务侧只保留 payment_no 引用
        String orderPaymentNo = jdbc.queryForObject(
                "SELECT payment_no FROM t_order WHERE order_no = ?", String.class, orderNo);
        assertThat(orderPaymentNo).isEqualTo(paymentNo);
    }

    @Test
    @DisplayName("attempt / event / refund / order 的 payment_no 引用全部回填")
    void paymentReferencesAreBackfilled() {
        String orderNo = createOrder(nextRequestId()).orderNo();
        pay(orderNo);
        paymentService.acceptVerified(successNotification(orderNo));

        assertThat(countNullPaymentNo("t_payment_attempt")).isZero();
        assertThat(countNullPaymentNo("t_payment_event")).isZero();
        assertThat(countNullPaymentNo("t_order")).isZero();

        String expected = PaymentShadowNo.of(orderNo);
        assertThat(jdbc.queryForList(
                "SELECT payment_no FROM t_payment_attempt WHERE order_id = "
                        + "(SELECT id FROM t_order WHERE order_no = ?)", String.class, orderNo))
                .containsOnly(expected);
        assertThat(jdbc.queryForList(
                "SELECT payment_no FROM t_payment_event WHERE order_no = ?", String.class, orderNo))
                .containsOnly(expected);
    }

    @Test
    @DisplayName("影子缺失或字段不一致时比对能报出来，重新同步后恢复零差异")
    void comparisonDetectsDriftAndSyncHeals() {
        String orderNo = createOrder(nextRequestId()).orderNo();
        pay(orderNo);
        paymentService.acceptVerified(successNotification(orderNo));
        String paymentNo = PaymentShadowNo.of(orderNo);
        assertClean();

        // 人为制造漂移：金额被改坏
        jdbc.update("UPDATE t_payment_order SET amount_cents = amount_cents + 1 WHERE payment_no = ?", paymentNo);
        ShadowComparison drifted = paymentShadowService.compare(100);
        assertThat(drifted.clean()).isFalse();
        assertThat(drifted.differences())
                .anyMatch(d -> d.field().equals("amountCents") && d.paymentNo().equals(paymentNo));

        // 重新同步（幂等）后恢复
        paymentShadowService.sync(orderNo);
        assertClean();

        // 整行缺失也能识别
        jdbc.update("DELETE FROM t_payment_order WHERE payment_no = ?", paymentNo);
        ShadowComparison missing = paymentShadowService.compare(100);
        assertThat(missing.differences())
                .anyMatch(d -> d.field().equals("MISSING") && d.orderNo().equals(orderNo));

        paymentShadowService.sync(orderNo);
        assertClean();
    }

    @Test
    @DisplayName("迟到的旧快照不会把支付单状态写回退")
    void staleSnapshotDoesNotRegressStatus() {
        String orderNo = createOrder(nextRequestId()).orderNo();
        pay(orderNo);
        paymentService.acceptVerified(successNotification(orderNo));
        String paymentNo = PaymentShadowNo.of(orderNo);
        assertThat(shadowStatus(paymentNo)).isEqualTo("PAID");

        // 模拟一次迟到同步：来源更新时间是一小时前、状态还是 CREATED
        paymentShadowRepository.upsert(new PaymentOrderShadowRow(
                paymentNo, PaymentOrderShadowService.APP_ID, PaymentOrderShadowService.BIZ_TYPE,
                orderNo, 0L, "迟到的旧快照", 1L, "CNY", "CREATED",
                null, null, null,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusMinutes(30), null));

        // 守卫列拦住整行更新：状态、金额、标题都不能被旧值覆盖
        // 注：Connector/J 默认返回 matched rows，upsert 返回值恒为 1，不能用来判断"是否更新"
        assertThat(shadowStatus(paymentNo)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject(
                "SELECT amount_cents FROM t_payment_order WHERE payment_no = ?", Long.class, paymentNo))
                .isEqualTo(fx.priceCents());
        assertThat(jdbc.queryForObject(
                "SELECT subject FROM t_payment_order WHERE payment_no = ?", String.class, paymentNo))
                .isNotEqualTo("迟到的旧快照");
    }

    private void assertClean() {
        ShadowComparison comparison = paymentShadowService.compare(100);
        assertThat(comparison.clean())
                .as("影子比对不一致：%s diffs=%s orphans=%s",
                        comparison.summary(), comparison.differences(), comparison.orphans())
                .isTrue();
    }

    private String shadowStatus(String paymentNo) {
        return jdbc.queryForObject(
                "SELECT status FROM t_payment_order WHERE payment_no = ?", String.class, paymentNo);
    }

    private int countNullPaymentNo(String table) {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE payment_no IS NULL", Integer.class);
        return value == null ? 0 : value;
    }
}
