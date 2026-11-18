package com.wherelee.edu.modules.payment;

/**
 * 支付渠道 SPI（策略）。
 *
 * <p>菜渠道适配器只接受支付域自己的 PaymentOrderView，不认识订单/课程等业务概念。</p>
 */
public interface PaymentGateway {

    String channel();

    /** 商户侧渠道单号：同一支付单同一尝试幂等。 */
    String outTradeNo(PaymentOrderView order, int attemptNo);

    PaymentAction initiate(PaymentOrderView order, PaymentAttemptRecord attempt);

    PaymentQueryResult query(PaymentOrderView order, PaymentAttemptRecord attempt);

    RefundResult refund(PaymentOrderView order, RefundRecord refund, boolean simulateFailure);

    record PaymentAction(String cashierUrl) {
    }

    record PaymentQueryResult(String status, long amountCents, String tradeNo, String message) {
    }

    record RefundResult(boolean success, String providerRefundNo, String message) {
    }
}
