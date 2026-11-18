package com.wherelee.base.modules.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public String channel() {
        return "MOCK";
    }

    @Override
    public String outTradeNo(PaymentOrderView order, int attemptNo) {
        return "MOCK-" + order.bizNo() + "-" + attemptNo;
    }

    @Override
    public PaymentAction initiate(PaymentOrderView order, PaymentAttemptRecord attempt) {
        return new PaymentAction("/mock-pay/" + order.bizNo());
    }

    @Override
    public PaymentQueryResult query(PaymentOrderView order, PaymentAttemptRecord attempt) {
        if (attempt == null) {
            return new PaymentQueryResult("PENDING", order.amountCents(), null, "尚无支付尝试");
        }
        return new PaymentQueryResult(
                attempt.providerStatus(),
                attempt.providerAmountCents(),
                attempt.providerTradeNo(),
                null
        );
    }

    @Override
    public RefundResult refund(PaymentOrderView order, RefundRecord refund, boolean simulateFailure) {
        if (simulateFailure) {
            return new RefundResult(false, null, "Mock 退款失败：模拟网关异常");
        }
        return new RefundResult(true, "MOCKRF-" + refund.refundNo(), null);
    }
}
