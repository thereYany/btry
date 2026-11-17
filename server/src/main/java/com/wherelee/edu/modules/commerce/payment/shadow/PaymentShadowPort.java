package com.wherelee.edu.modules.commerce.payment.shadow;

/**
 * 支付单影子写的触发端口。
 *
 * <p>存在的意义：A 期把支付从「订单的附属」拆成独立聚合，但读路径仍走旧模型。
 * 业务侧只负责在状态变化后调用 {@link #recordAfterCommit(String)}，不感知影子表的实现。</p>
 *
 * <p>实现必须在事务提交后执行，避免影子写失败影响资金主链路；单元测试用 {@link #NOOP}。</p>
 */
@FunctionalInterface
public interface PaymentShadowPort {

    void recordAfterCommit(String orderNo);

    PaymentShadowPort NOOP = orderNo -> {
    };
}
