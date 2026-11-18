package com.wherelee.edu.modules.commerce.payment;

/**
 * 支付单号生成规则：{@code P + 业务单号}。
 *
 * <p>确定性生成保证「同一业务单只有一个支付单」，重复发起是幂等的；
 * 正式多业务方接入后改由序列/雪花生成，届时需要一次性迁移。</p>
 */
public final class PaymentNo {

    private static final String PREFIX = "P";

    private PaymentNo() {
    }

    public static String of(String bizNo) {
        return PREFIX + bizNo;
    }
}
