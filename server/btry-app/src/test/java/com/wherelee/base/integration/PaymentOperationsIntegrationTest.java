package com.wherelee.base.integration;

import com.wherelee.base.modules.contracts.payment.PaymentOperations;
import com.wherelee.base.modules.payment.PaymentConfirmationService;
import com.wherelee.base.modules.payment.PaymentNotification;
import com.wherelee.base.modules.payment.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内核证据测试：支付域完全脱离业务订单运行。
 *
 * <p>任意 bizType/bizNo 都能发起支付、落支付单/尝试/事件/发件箱，
 * 说明 btry 的支付能力可以被任意垂直应用复用。</p>
 */
class PaymentOperationsIntegrationTest extends KernelIntegrationTestSupport {

    @Autowired
    private PaymentOperations paymentOperations;
    @Autowired
    private PaymentConfirmationService confirmationService;
    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    @DisplayName("任意 bizType/bizNo 可发起支付，支付域自动建单建尝试且幂等")
    void genericInitiationCreatesPaymentOrderAndAttempt() {
        PaymentOperations.InitiateResult result = paymentOperations.initiate(
                new PaymentOperations.InitiateCommand(null, "RESOURCE_PACK", "RP-1",
                        9900L, "CNY", "资料包-数据结构", LocalDateTime.now().plusMinutes(15)));

        assertThat(result.status()).isEqualTo("PAYING");
        assertThat(result.paymentNo()).startsWith("P");
        assertThat(result.cashierUrl()).isNotBlank();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM t_payment_order WHERE payment_no = ? AND tenant_id = 0",
                String.class, result.paymentNo())).isEqualTo("PAYING");
        assertThat(count("t_payment_attempt", "payment_no = ?", result.paymentNo())).isEqualTo(1);

        PaymentOperations.InitiateResult second = paymentOperations.initiate(
                new PaymentOperations.InitiateCommand(null, "RESOURCE_PACK", "RP-1",
                        9900L, "CNY", "资料包-数据结构", LocalDateTime.now().plusMinutes(15)));
        assertThat(second.paymentNo()).isEqualTo(result.paymentNo());
        assertThat(count("t_payment_attempt", "payment_no = ?", result.paymentNo())).isEqualTo(1);
    }

    @Test
    @DisplayName("支付成功写入发件箱，业务侧只需监听事件并自行履约")
    void genericPaymentSettlesAndEmitsBusinessEvent() {
        PaymentOperations.InitiateResult result = paymentOperations.initiate(
                new PaymentOperations.InitiateCommand(null, "RESOURCE_PACK", "RP-2",
                        9900L, "CNY", "资料包-算法", LocalDateTime.now().plusMinutes(15)));
        String tradeNo = paymentRepository.findLatestAttemptByPaymentNo(result.paymentNo())
                .orElseThrow().providerTradeNo();

        confirmationService.accept(new PaymentNotification(
                "MOCK", "IT:RP-2:PAID", "RP-2", "PAYMENT_SUCCESS", 9900L, tradeNo, null));

        assertThat(jdbc.queryForObject(
                "SELECT status FROM t_payment_order WHERE payment_no = ?", String.class,
                result.paymentNo())).isEqualTo("PAID");
        assertThat(paymentOperations.findByBiz("RESOURCE_PACK", "RP-2").orElseThrow().status())
                .isEqualTo("PAID");
        assertThat(count("t_payment_outbox",
                "aggregate_no = ? AND payload LIKE '%RESOURCE_PACK%'", result.paymentNo())).isEqualTo(1);
    }
}
