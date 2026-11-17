package com.wherelee.edu.modules.commerce.payment.shadow;

import java.time.LocalDateTime;

/**
 * 影子期写入 t_payment_order 的一行数据。
 *
 * <p>字段刻意与「订单」解耦：只有 appId/bizType/bizNo 指向业务，没有任何 course/order 语义。</p>
 */
public record PaymentOrderShadowRow(
        String paymentNo,
        String appId,
        String bizType,
        String bizNo,
        long tenantId,
        String subject,
        long amountCents,
        String currency,
        String status,
        String channel,
        String providerTradeNo,
        String metadataJson,
        LocalDateTime sourceUpdatedAt,
        LocalDateTime expireAt,
        LocalDateTime paidAt
) {
}
