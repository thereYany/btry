package com.wherelee.base.modules.payment;

import com.wherelee.base.modules.payment.api.PaymentEventTypes;
import com.wherelee.base.modules.payment.api.PaymentSettledEvent;
import com.wherelee.base.modules.payment.outbox.PaymentOutboxRepository;
import com.wherelee.base.modules.platform.alert.AlertPublisher;
import com.wherelee.base.modules.platform.tracing.TraceSpans;
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
class PaymentConfirmationServiceTest {

    @Mock private PaymentOrderRepository paymentOrderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentOutboxRepository outboxRepository;
    @Mock private AlertPublisher alertPublisher;
    @Mock private PaymentMetrics metrics;

    private PaymentConfirmationService service;

    @BeforeEach
    void setUp() {
        service = new PaymentConfirmationService(paymentOrderRepository, paymentRepository,
                outboxRepository, alertPublisher, metrics, TraceSpans.noop());
    }

    @Test
    void successEventMarksPaymentPaidAndWritesOutbox() {
        when(paymentOrderRepository.findByBizNo("ORD-1")).thenReturn(Optional.of(order("PAYING")));
        when(paymentRepository.insertEvent(anyString(), anyString(), anyString(), anyString(),
                anyString(), eq(19900L), any())).thenReturn(true);
        when(paymentRepository.findEvent("MOCK", "EVT-1")).thenReturn(Optional.of(event()));
        when(paymentOrderRepository.findByPaymentNo("P-ORD-1")).thenReturn(Optional.of(order("PAID")));

        var result = service.accept(notification("PAYMENT_SUCCESS", 19900L));

        assertThat(result.success()).isTrue();
        assertThat(result.status()).isEqualTo("PAID");
        verify(paymentOrderRepository).markPaid("P-ORD-1", "MOCK", "MOCK-1");
        ArgumentCaptor<PaymentSettledEvent> captor = ArgumentCaptor.forClass(PaymentSettledEvent.class);
        verify(outboxRepository).append(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(PaymentEventTypes.PAYMENT_SUCCEEDED);
        assertThat(captor.getValue().bizNo()).isEqualTo("ORD-1");
    }

    @Test
    void duplicateEventIsIgnoredWithoutStateChange() {
        when(paymentOrderRepository.findByBizNo("ORD-1")).thenReturn(Optional.of(order("PAID")));
        when(paymentRepository.insertEvent(anyString(), anyString(), anyString(), anyString(),
                anyString(), eq(19900L), any())).thenReturn(false);
        when(paymentRepository.findEvent("MOCK", "EVT-1")).thenReturn(Optional.of(event()));

        var result = service.accept(notification("PAYMENT_SUCCESS", 19900L));

        assertThat(result.duplicate()).isTrue();
        verify(paymentOrderRepository, never()).markPaid(anyString(), anyString(), anyString());
        verify(outboxRepository, never()).append(any());
        verify(metrics).callbackDuplicate();
    }

    @Test
    void amountMismatchIsRejectedAndAlerted() {
        when(paymentOrderRepository.findByBizNo("ORD-1")).thenReturn(Optional.of(order("PAYING")));
        when(paymentRepository.insertEvent(anyString(), anyString(), anyString(), anyString(),
                anyString(), eq(9900L), any())).thenReturn(true);

        var result = service.accept(notification("PAYMENT_SUCCESS", 9900L));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("金额不一致");
        verify(paymentOrderRepository, never()).markPaid(anyString(), anyString(), anyString());
        verify(metrics).amountMismatch();
        verify(alertPublisher).publish(eq("PAYMENT_AMOUNT_MISMATCH"), eq("ERROR"),
                anyString(), eq(false), anyString());
    }

    @Test
    void unknownPaymentIsRejected() {
        when(paymentOrderRepository.findByBizNo("ORD-1")).thenReturn(Optional.empty());

        var result = service.accept(notification("PAYMENT_SUCCESS", 19900L));

        assertThat(result.success()).isFalse();
        assertThat(result.paymentNo()).isNull();
    }

    private PaymentNotification notification(String eventType, long amount) {
        return new PaymentNotification("MOCK", "EVT-1", "ORD-1", eventType, amount, "MOCK-1", null);
    }

    private PaymentOrderView order(String status) {
        return new PaymentOrderView("P-ORD-1", "ORD-1", "btry", "SAMPLE_ORDER", "示例订单",
                19900L, "CNY", status, "MOCK", "MOCK-1",
                LocalDateTime.now().plusMinutes(15), "PAID".equals(status) ? LocalDateTime.now() : null);
    }

    private PaymentEventRecord event() {
        return new PaymentEventRecord(1L, "MOCK", "EVT-1", null, "ORD-1", "PAYMENT_SUCCESS",
                19900L, "PROCESSED", null, LocalDateTime.now(), LocalDateTime.now());
    }
}
