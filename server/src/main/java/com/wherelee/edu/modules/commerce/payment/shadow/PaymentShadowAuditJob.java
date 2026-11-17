package com.wherelee.edu.modules.commerce.payment.shadow;

import com.wherelee.edu.modules.commerce.payment.shadow.PaymentOrderShadowService.ShadowComparison;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 支付单影子比对巡检。
 *
 * <p>影子期最重要的不是"写进去了"，而是"能持续证明两边一致"。这个任务把比对结果
 * 变成指标 {@code payment.shadow.differences} 和日志，差异会自己浮出来，而不是等人去查。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.payment.shadow.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentShadowAuditJob {

    private final PaymentOrderShadowService shadowService;
    private final int batchSize;
    private final AtomicInteger differenceCount = new AtomicInteger();

    public PaymentShadowAuditJob(PaymentOrderShadowService shadowService,
                                 @Value("${app.payment.shadow.audit-batch-size:500}") int batchSize,
                                 MeterRegistry meterRegistry) {
        this.shadowService = shadowService;
        this.batchSize = batchSize;
        Gauge.builder("payment.shadow.differences", differenceCount, AtomicInteger::get)
                .description("支付单影子模型与订单事实的差异条数（差异 + 未回填引用）")
                .register(meterRegistry);
    }

    @Scheduled(
            fixedDelayString = "${app.payment.shadow.audit-interval-ms:600000}",
            initialDelayString = "${app.payment.shadow.audit-initial-delay-ms:120000}"
    )
    public void audit() {
        try {
            ShadowComparison comparison = shadowService.compare(batchSize);
            differenceCount.set(comparison.differences().size() + comparison.orphans().total());
            if (comparison.clean()) {
                log.info("支付单影子比对通过 {}", comparison.summary());
                return;
            }
            log.warn("支付单影子比对发现差异 {}", comparison.summary());
            comparison.differences().stream().limit(20).forEach(diff -> log.warn("  差异 {}", diff));
            if (comparison.orphans().total() > 0) {
                log.warn("  未回填引用行 attempts={} events={} refunds={} orders={}",
                        comparison.orphans().attempts(), comparison.orphans().events(),
                        comparison.orphans().refunds(), comparison.orphans().orders());
            }
        } catch (Exception e) {
            log.error("支付单影子比对失败", e);
        }
    }

    /** 供测试与运维读取最近一次比对结果。 */
    public int lastDifferenceCount() {
        return differenceCount.get();
    }
}
