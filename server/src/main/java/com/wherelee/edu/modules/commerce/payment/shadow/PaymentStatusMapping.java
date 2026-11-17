package com.wherelee.edu.modules.commerce.payment.shadow;

import com.wherelee.edu.modules.commerce.order.OrderStatus;

/**
 * 订单状态 → 支付单状态映射。
 *
 * <p>支付单只表达「钱」：订单履约完成仍然是 PAID；退款在进行中是 REFUNDING。
 * 两边状态机不同，映射必须是显式的、单向的，不做"看起来差不多"的猜测。</p>
 */
public final class PaymentStatusMapping {

    private PaymentStatusMapping() {
    }

    public static String fromOrder(OrderStatus status) {
        return switch (status) {
            case CREATED -> "CREATED";
            case PAYING -> "PAYING";
            case PAY_FAILED -> "FAILED";
            case PAID, FULFILLED -> "PAID";
            case REFUNDING -> "REFUNDING";
            case REFUNDED -> "REFUNDED";
            case REFUND_FAILED -> "REFUND_FAILED";
            case CLOSED -> "CLOSED";
        };
    }
}
