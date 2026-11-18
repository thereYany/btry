package com.wherelee.base.modules.payment.outbox;

import com.wherelee.base.modules.payment.api.PaymentSettledEvent;
import com.wherelee.base.modules.payment.api.PaymentSettledListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 发件箱投递器：把支付事件可靠投递给业务侧监听者。
 *
 * <p>语义是 at-least-once，所以监听者必须幂等。投递失败按指数退避重试，
 * 超过上限进入 DEAD（死信），由指标与告警暴露，人工处理——不静默吞掉。</p>
 */
@Slf4j
@Component
public class PaymentOutboxDispatcher {

    private final PaymentOutboxRepository repository;
    private final List<PaymentSettledListener> listeners;
    private final int batchSize;
    private final int maxAttempts;
    private final int backoffBaseSeconds;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger dead = new AtomicInteger();
    private final Counter sent;
    private final Counter failed;
    private final Counter deadLettered;

    public PaymentOutboxDispatcher(PaymentOutboxRepository repository,
                                   List<PaymentSettledListener> listeners,
                                   MeterRegistry meterRegistry,
                                   @Value("${app.payment.outbox.batch-size:50}") int batchSize,
                                   @Value("${app.payment.outbox.max-attempts:5}") int maxAttempts,
                                   @Value("${app.payment.outbox.backoff-base-seconds:5}") int backoffBaseSeconds) {
        this.repository = repository;
        this.listeners = listeners;
        this.batchSize = Math.max(1, batchSize);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.backoffBaseSeconds = Math.max(1, backoffBaseSeconds);
        this.sent = meterRegistry.counter("payment.outbox.sent");
        this.failed = meterRegistry.counter("payment.outbox.failed");
        this.deadLettered = meterRegistry.counter("payment.outbox.dead_lettered");
        Gauge.builder("payment.outbox.in_flight", inFlight, AtomicInteger::get)
                .description("待投递/投递中的支付事件数量").register(meterRegistry);
        Gauge.builder("payment.outbox.dead", dead, AtomicInteger::get)
                .description("进入死信的支付事件数量").register(meterRegistry);
    }

    @Scheduled(
            fixedDelayString = "${app.payment.outbox.interval-ms:5000}",
            initialDelayString = "${app.payment.outbox.initial-delay-ms:10000}")
    public void run() {
        try {
            dispatchOnce();
        } catch (Exception e) {
            log.error("支付发件箱投递任务异常", e);
        }
    }

    /** 同步执行一轮投递（测试与手动补偿用）。 */
    public int dispatchOnce() {
        List<PaymentOutboxRepository.OutboxRow> rows = repository.claimDue(batchSize);
        int delivered = 0;
        for (PaymentOutboxRepository.OutboxRow row : rows) {
            try {
                PaymentSettledEvent event = repository.payload(row);
                for (PaymentSettledListener listener : listeners) {
                    listener.onSettled(event);
                }
                repository.markSent(row.id());
                sent.increment();
                delivered++;
            } catch (Exception e) {
                int attempts = row.attempts() + 1;
                if (attempts >= maxAttempts) {
                    repository.markDead(row.id(), e.toString());
                    deadLettered.increment();
                    log.error("支付事件投递失败进入死信 id={} eventType={} attempts={}",
                            row.id(), row.eventType(), attempts, e);
                } else {
                    repository.reschedule(row.id(), attempts,
                            backoffBaseSeconds * attempts, e.toString());
                    failed.increment();
                    log.warn("支付事件投递失败，稍后重试 id={} attempts={} cause={}",
                            row.id(), attempts, e.toString());
                }
            }
        }
        inFlight.set(repository.countInFlight());
        dead.set(repository.countDead());
        if (delivered > 0) {
            log.info("支付发件箱投递完成 delivered={} inFlight={}", delivered, inFlight.get());
        }
        return delivered;
    }
}
