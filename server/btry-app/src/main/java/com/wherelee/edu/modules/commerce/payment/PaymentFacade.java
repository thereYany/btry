package com.wherelee.edu.modules.commerce.payment;

import com.wherelee.edu.modules.contracts.commerce.PaymentOperations;
import com.wherelee.edu.modules.platform.common.BizException;
import com.wherelee.edu.modules.platform.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 通用支付门面：把"业务单 → 支付单 → 渠道"的流程收在一处，供任意垂直域复用。
 *
 * <p>课程订单走同一个入口（传入固定 paymentNo 兼容历史单号）；新垂直域只需给 bizType/bizNo。</p>
 */
@Slf4j
@Service
public class PaymentFacade implements PaymentOperations {

    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    public PaymentFacade(PaymentOrderRepository paymentOrderRepository,
                         PaymentRepository paymentRepository,
                         PaymentGateway paymentGateway) {
        this.paymentOrderRepository = paymentOrderRepository;
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
    }

    @Override
    @Transactional
    public InitiateResult initiate(InitiateCommand command) {
        validate(command);
        String paymentNo = command.paymentNo() == null || command.paymentNo().isBlank()
                ? PaymentNo.issue()
                : command.paymentNo();
        PaymentOrderView view = new PaymentOrderView(
                paymentNo, command.bizNo(), "btry", command.bizType(), command.subject(),
                command.amountCents(), command.currency(), "CREATED", paymentGateway.channel(),
                null, command.expireAt(), null);
        paymentOrderRepository.insertIfAbsent(view, TenantContext.current(), LocalDateTime.now());

        PaymentOrderView payment = paymentOrderRepository
                .findByBiz(command.bizType(), command.bizNo())
                .orElseThrow(() -> new IllegalStateException("支付单创建后不可读取"));
        if ("PAID".equals(payment.status()) || "REFUNDED".equals(payment.status())
                || "CLOSED".equals(payment.status())) {
            return new InitiateResult(payment.paymentNo(), payment.status(),
                    payment.channel(), null);
        }

        PaymentAttemptRecord attempt = paymentRepository
                .findLatestAttemptByPaymentNo(payment.paymentNo()).orElse(null);
        if (attempt == null || !"PROCESSING".equals(attempt.status())) {
            int attemptNo = paymentRepository.nextAttemptNoByPaymentNo(payment.paymentNo());
            String tradeNo = paymentGateway.outTradeNo(payment, attemptNo);
            paymentRepository.createAttempt(null, payment.paymentNo(), attemptNo,
                    paymentGateway.channel(), tradeNo, payment.amountCents(),
                    payment.paymentNo() + "|" + attemptNo + "|" + payment.amountCents());
            attempt = paymentRepository.findLatestAttemptByPaymentNo(payment.paymentNo())
                    .orElseThrow(() -> new IllegalStateException("支付尝试创建后不可读取"));
        }
        paymentOrderRepository.markPaying(payment.paymentNo(), paymentGateway.channel(),
                attempt.providerTradeNo());
        PaymentGateway.PaymentAction action = paymentGateway.initiate(payment, attempt);
        log.info("payment_initiated paymentNo={} bizType={} bizNo={} amountCents={}",
                payment.paymentNo(), payment.bizType(), payment.bizNo(), payment.amountCents());
        return new InitiateResult(payment.paymentNo(), "PAYING",
                paymentGateway.channel(), action.cashierUrl());
    }

    @Override
    public Optional<PaymentView> findByBiz(String bizType, String bizNo) {
        return paymentOrderRepository.findByBiz(bizType, bizNo).map(payment -> new PaymentView(
                payment.paymentNo(), payment.bizType(), payment.bizNo(), payment.status(),
                payment.amountCents(), payment.currency(), payment.channel(),
                payment.providerTradeNo(), payment.paidAt()));
    }

    private void validate(InitiateCommand command) {
        if (command.bizType() == null || command.bizType().isBlank()
                || command.bizNo() == null || command.bizNo().isBlank()) {
            throw BizException.badRequest("bizType/bizNo 不能为空");
        }
        if (command.amountCents() <= 0) {
            throw BizException.badRequest("支付金额必须大于 0");
        }
        if (command.currency() == null || command.currency().isBlank()) {
            throw BizException.badRequest("币种不能为空");
        }
    }
}
