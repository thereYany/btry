package com.wherelee.edu.modules.contracts.payment;

import java.time.LocalDateTime;

/**
 * 支付异常巡检所需的只读查询契约。
 *
 * <p>operations.alert 只依赖本接口做告警统计，不直接接触 payment 的 Repository 与配置。</p>
 */
public interface PaymentAlertQueries {

    long countPaidUnfulfilledBefore(LocalDateTime before);

    long countPayingBefore(LocalDateTime before);

    long countRefundRetryExhausted();
}
