package com.wherelee.edu.modules.commerce.payment.shadow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 把影子写挂到事务提交之后。
 *
 * <p>为什么用 afterCommit 而不是直接同步写：影子表是新增的试验模型，
 * 它写失败不能导致真实支付失败。但也不能用异步线程——那样比对测试会有竞态。
 * afterCommit 是同步执行的，既不影响主事务提交，又能让调用方返回时影子已就绪。</p>
 */
@Slf4j
@Component
public class PaymentShadowRecorder implements PaymentShadowPort {

    private final PaymentOrderShadowService shadowService;
    private final boolean enabled;

    public PaymentShadowRecorder(PaymentOrderShadowService shadowService,
                                 @Value("${app.payment.shadow.enabled:true}") boolean enabled) {
        this.shadowService = shadowService;
        this.enabled = enabled;
    }

    @Override
    public void recordAfterCommit(String orderNo) {
        if (!enabled || orderNo == null || orderNo.isBlank()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safeSync(orderNo);
                }
            });
            return;
        }
        safeSync(orderNo);
    }

    private void safeSync(String orderNo) {
        try {
            shadowService.sync(orderNo);
        } catch (Exception e) {
            // 影子写失败不阻断资金链路，但必须留痕——比对任务会把它暴露成差异。
            log.warn("支付单影子写失败 orderNo={} cause={}", orderNo, e.toString());
        }
    }
}
