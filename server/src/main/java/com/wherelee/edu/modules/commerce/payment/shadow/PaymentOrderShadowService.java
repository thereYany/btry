package com.wherelee.edu.modules.commerce.payment.shadow;

import com.wherelee.edu.modules.commerce.order.OrderRecord;
import com.wherelee.edu.modules.commerce.order.OrderRepository;
import com.wherelee.edu.modules.commerce.order.OrderStatus;
import com.wherelee.edu.modules.commerce.payment.PaymentAttemptRecord;
import com.wherelee.edu.modules.commerce.payment.PaymentRepository;
import com.wherelee.edu.modules.commerce.payment.shadow.PaymentOrderShadowRepository.ReferenceOrphans;
import com.wherelee.edu.modules.commerce.payment.shadow.PaymentOrderShadowRepository.ShadowPair;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 影子写与双读比对。
 *
 * <p>影子期（A 期）的唯一目标：证明「支付单独立模型」能完整承载现有订单数据，
 * 且不改变任何读路径。因此这里只做两件事——把订单事实同步成支付单，以及把两边读出来比对。</p>
 *
 * <p>注意：本类位于 payment 包但依赖 order，属于**过渡态**。当 A 期结束、履约改为事件驱动后，
 * 这个依赖会被移除（ArchUnit 规则 R5 届时生效）。</p>
 */
@Slf4j
@Service
public class PaymentOrderShadowService {

    /** 影子期固定接入方标识；正式平台化后由应用注册表提供。 */
    public static final String APP_ID = "btry";
    public static final String BIZ_TYPE = "COURSE_ORDER";

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentOrderShadowRepository shadowRepository;

    public PaymentOrderShadowService(OrderRepository orderRepository,
                                     PaymentRepository paymentRepository,
                                     PaymentOrderShadowRepository shadowRepository) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.shadowRepository = shadowRepository;
    }

    /**
     * 从订单事实同步一笔支付单。幂等：重复调用结果一致。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sync(String orderNo) {
        OrderRecord order = orderRepository.findByOrderNo(orderNo).orElse(null);
        if (order == null) {
            log.debug("支付单影子同步跳过：订单不存在 orderNo={}", orderNo);
            return;
        }
        PaymentAttemptRecord attempt = paymentRepository.findLatestAttempt(order.id()).orElse(null);
        String paymentNo = PaymentShadowNo.of(order.orderNo());
        String channel = firstNonNull(order.payChannel(), attempt == null ? null : attempt.channel());
        String providerTradeNo = firstNonNull(
                order.providerTradeNo(), attempt == null ? null : attempt.providerTradeNo());
        PaymentOrderShadowRow row = new PaymentOrderShadowRow(
                paymentNo,
                APP_ID,
                BIZ_TYPE,
                order.orderNo(),
                0L,
                order.seriesTitle(),
                order.priceCents(),
                order.currency(),
                PaymentStatusMapping.fromOrder(order.status()),
                channel,
                providerTradeNo,
                "{\"seriesId\":" + order.seriesId() + "}",
                order.updatedAt(),
                order.expireAt(),
                order.paidAt()
        );
        shadowRepository.upsert(row);
        int backfilled = shadowRepository.backfillReferences(order.id(), order.orderNo(), paymentNo);
        log.debug("支付单影子同步 orderNo={} paymentNo={} status={} backfilled={}",
                order.orderNo(), paymentNo, row.status(), backfilled);
    }

    /**
     * 双读比对：把最近 N 笔订单与影子支付单逐字段对照。
     */
    @Transactional(readOnly = true)
    public ShadowComparison compare(int orderLimit) {
        List<ShadowPair> pairs = shadowRepository.findPairs(Math.max(1, orderLimit));
        List<Difference> differences = new ArrayList<>();
        for (ShadowPair pair : pairs) {
            if (pair.paymentNo() == null) {
                differences.add(new Difference(pair.orderNo(), null, "MISSING", "存在", "缺失"));
                continue;
            }
            String expectedStatus = PaymentStatusMapping.fromOrder(OrderStatus.valueOf(pair.orderStatus()));
            if (!Objects.equals(expectedStatus, pair.payStatus())) {
                differences.add(new Difference(pair.orderNo(), pair.paymentNo(),
                        "status", expectedStatus, pair.payStatus()));
            }
            if (pair.payAmountCents() == null || pair.payAmountCents() != pair.orderAmountCents()) {
                differences.add(new Difference(pair.orderNo(), pair.paymentNo(),
                        "amountCents", pair.orderAmountCents(), pair.payAmountCents()));
            }
            // 与 sync 取值口径一致：订单字段优先，未落订单前用最新一次支付尝试兜底
            String expectedChannel = firstNonNull(pair.orderChannel(), pair.attemptChannel());
            String expectedTradeNo = firstNonNull(pair.orderTradeNo(), pair.attemptTradeNo());
            if (!Objects.equals(expectedChannel, pair.payChannel())) {
                differences.add(new Difference(pair.orderNo(), pair.paymentNo(),
                        "channel", expectedChannel, pair.payChannel()));
            }
            if (!Objects.equals(expectedTradeNo, pair.payTradeNo())) {
                differences.add(new Difference(pair.orderNo(), pair.paymentNo(),
                        "providerTradeNo", expectedTradeNo, pair.payTradeNo()));
            }
        }
        ReferenceOrphans orphans = shadowRepository.countOrphans();
        return new ShadowComparison(pairs.size(), differences, orphans);
    }

    private static String firstNonNull(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    /** 比对结果。clean() 为 true 表示影子模型与源单据完全一致。 */
    public record ShadowComparison(int checkedOrders, List<Difference> differences, ReferenceOrphans orphans) {

        public boolean clean() {
            return differences.isEmpty() && orphans.total() == 0;
        }

        public String summary() {
            return "checked=" + checkedOrders
                    + " diffs=" + differences.size()
                    + " orphans=" + orphans.total();
        }
    }

    /** 单条差异：哪张单、哪个字段、期望值与实际值。 */
    public record Difference(String orderNo, String paymentNo, String field, Object expected, Object actual) {
        @Override
        public String toString() {
            return "orderNo=" + orderNo + " paymentNo=" + paymentNo + " field=" + field
                    + " expected=" + expected + " actual=" + actual;
        }
    }
}
