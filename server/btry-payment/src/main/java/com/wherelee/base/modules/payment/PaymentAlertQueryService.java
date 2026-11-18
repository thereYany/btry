package com.wherelee.base.modules.payment;

import com.wherelee.base.modules.contracts.payment.PaymentAlertQueries;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class PaymentAlertQueryService implements PaymentAlertQueries {

    private final PaymentRepository paymentRepository;
    private final PaymentProperties paymentProperties;

    public PaymentAlertQueryService(PaymentRepository paymentRepository,
                                    PaymentProperties paymentProperties) {
        this.paymentRepository = paymentRepository;
        this.paymentProperties = paymentProperties;
    }

    @Override
    public long countPaidUnfulfilledBefore(LocalDateTime before) {
        return paymentRepository.countPaidUnfulfilledBefore(before);
    }

    @Override
    public long countPayingBefore(LocalDateTime before) {
        return paymentRepository.countPayingBefore(before);
    }

    @Override
    public long countRefundRetryExhausted() {
        return paymentRepository.countRefundRetryExhausted(paymentProperties.safeRefundMaxAttempts());
    }
}
