package com.wherelee.edu.modules.payment;

import com.wherelee.edu.modules.payment.api.PaymentEventTypes;
import com.wherelee.edu.modules.payment.api.PaymentSettledEvent;
import com.wherelee.edu.modules.payment.outbox.PaymentOutboxRepository;
import com.wherelee.edu.modules.platform.tracing.TraceSpans;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentQueryServiceTest {

    @Mock private PaymentOrderRepository paymentOrderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentGateway paymentGateway;
    @Mock private PaymentConfirmationService confirmationService;
    @Mock private PaymentOutboxRepository outboxRepository;

    private PaymentQueryService service;

    @BeforeEach
    void setUp() {
        service = new PaymentQueryService(paymentOrderRepository, paymentRepository, paymentGateway,
                confirmationService, outboxRepository,
                new PaymentProperties("mock", true, null, 15, 60, 50, 60000, 30000, 5, 30),
                TraceSpans.noop());
    }

    @Test
    void paidQueryProducesSucceededEventThroughConfirmation() {
        when(paymentRepository.findLatestAttemptByPaymentNo("P-ORD-1")).thenReturn(Optional.of(attempt()));
        when(paymentGateway.query(any(PaymentOrderView.class), any())).thenReturn(
                new PaymentGateway.PaymentQueryResult("PAID", 19900L, "MOCK-T-1", null));

        assertThat(service.query(payment(LocalDateTime.now().plusMinutes(5)))).isTrue();

        ArgumentCaptor<PaymentNotification> captor = ArgumentCaptor.forClass(PaymentNotification.class);
        verify(confirmationService).accept(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("PAYMENT_SUCCESS");
        assertThat(captor.getValue().eventKey()).isEqualTo("QUERY:P-ORD-1:MOCK-T-1");
        verify(paymentOrderRepository, never()).markClosed(anyString());
    }

    @Test
    void expiredUntradedPaymentIsClosedAndExpiredEventAppended() {
        when(paymentRepository.findLatestAttemptByPaymentNo("P-ORD-1")).thenReturn(Optional.of(attempt()));
        when(paymentGateway.query(any(PaymentOrderView.class), any())).thenReturn(
                new PaymentGateway.PaymentQueryResult("NOT_FOUND", 19900L, "MOCK-T-1", "ACQ.TRADE_NOT_EXIST"));
        when(paymentOrderRepository.markClosed("P-ORD-1")).thenReturn(1);

        assertThat(service.query(payment(LocalDateTime.now().minusMinutes(1)))).isTrue();

        verify(paymentOrderRepository).markClosed("P-ORD-1");
        ArgumentCaptor<PaymentSettledEvent> captor = ArgumentCaptor.forClass(PaymentSettledEvent.class);
        verify(outboxRepository).append(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(PaymentEventTypes.PAYMENT_EXPIRED);
        assertThat(captor.getValue().bizNo()).isEqualTo("ORD-1");
        verify(confirmationService, never()).accept(any());
    }

    @Test
    void pendingPaymentIsLeftAlone() {
        when(paymentRepository.findLatestAttemptByPaymentNo("P-ORD-1")).thenReturn(Optional.empty());
        when(paymentGateway.query(any(PaymentOrderView.class), eq(null))).thenReturn(
                new PaymentGateway.PaymentQueryResult("PENDING", 19900L, null, null));

        assertThat(service.query(payment(LocalDateTime.now().plusMinutes(5)))).isFalse();
        verify(paymentOrderRepository, never()).markClosed(anyString());
        verify(outboxRepository, never()).append(any());
    }

    private PaymentOrderView payment(LocalDateTime expireAt) {
        return new PaymentOrderView("P-ORD-1", "ORD-1", "btry", "COURSE_ORDER", "物理必修一",
                19900L, "CNY", "PAYING", "MOCK", "MOCK-1", expireAt, null);
    }

    private PaymentAttemptRecord attempt() {
        return new PaymentAttemptRecord(1L, null, 1, "MOCK", "PROCESSING", "MOCK-1",
                "CREATED", 19900L, "digest", null, null, null,
                LocalDateTime.now(), null);
    }
}
