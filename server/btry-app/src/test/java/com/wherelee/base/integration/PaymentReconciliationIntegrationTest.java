package com.wherelee.base.integration;

import com.wherelee.base.modules.contracts.payment.AdminReconciliationOperations;
import com.wherelee.base.modules.contracts.payment.AdminReconciliationOperations.ReconSummary;
import com.wherelee.base.modules.contracts.payment.PaymentOperations;
import com.wherelee.base.modules.payment.PaymentConfirmationService;
import com.wherelee.base.modules.payment.PaymentNotification;
import com.wherelee.base.modules.payment.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内核证据测试：对账链路只依赖支付单/渠道账单，不依赖任何业务订单。
 */
class PaymentReconciliationIntegrationTest extends KernelIntegrationTestSupport {

    @Autowired
    private PaymentOperations paymentOperations;
    @Autowired
    private PaymentConfirmationService confirmationService;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private AdminReconciliationOperations reconciliation;

    @Test
    @DisplayName("账单与支付单一致时零差异，重跑幂等")
    void matchedBillProducesZeroDiffAndIsIdempotent() {
        PaymentOperations.InitiateResult result = paymentOperations.initiate(
                new PaymentOperations.InitiateCommand(null, "MEMBERSHIP", "MB-1",
                        19900L, "CNY", "会员月卡", LocalDateTime.now().plusMinutes(15)));
        String tradeNo = paymentRepository.findLatestAttemptByPaymentNo(result.paymentNo())
                .orElseThrow().providerTradeNo();
        confirmationService.accept(new PaymentNotification(
                "MOCK", "IT:MB-1:PAID", "MB-1", "PAYMENT_SUCCESS", 19900L, tradeNo, null));

        ReconSummary first = reconciliation.run("MOCK", LocalDate.now());
        assertThat(first.totalCount()).isEqualTo(1);
        assertThat(first.matchedCount()).isEqualTo(1);
        assertThat(first.diffCount()).isZero();

        ReconSummary second = reconciliation.run("MOCK", LocalDate.now());
        assertThat(second.taskNo()).isEqualTo(first.taskNo());
        assertThat(second.diffCount()).isZero();
    }
}
