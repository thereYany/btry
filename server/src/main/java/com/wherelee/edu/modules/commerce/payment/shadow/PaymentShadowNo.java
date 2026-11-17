package com.wherelee.edu.modules.commerce.payment.shadow;

/**
 * 影子期支付单号规则：{@code P + 订单号}。
 *
 * <p>确定性生成，保证重复同步幂等；正式切换后改由序列/雪花 ID 生成，
 * 届时需要一次性迁移（见 docs/数据所有权与分库边界.md 的 contract 阶段）。</p>
 */
public final class PaymentShadowNo {

    private static final String PREFIX = "P";

    private PaymentShadowNo() {
    }

    public static String of(String orderNo) {
        return PREFIX + orderNo;
    }
}
