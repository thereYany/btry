package com.wherelee.base.app.alert;

import com.wherelee.base.modules.contracts.payment.PaymentAlertQueries;
import com.wherelee.base.modules.platform.alert.AlertPublisher;
import com.wherelee.base.modules.platform.resilience.LocalFallbackCache;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentAlertJobTest {

    @Test
    void emitsPaidUnfulfilledAlert() {
        AlertProperties properties = new AlertProperties(
                true, null, 30, 300000, 60000, 1, 15, 1);
        PaymentAlertQueries queries = mock(PaymentAlertQueries.class);
        AlertNotifier notifier = mock(AlertNotifier.class);
        AlertPublisher publisher = mock(AlertPublisher.class);
        LocalFallbackCache dedup = mock(LocalFallbackCache.class);
        when(queries.countSettlementUndeliveredBefore(any())).thenReturn(1L);
        when(queries.countPayingPaymentBefore(any())).thenReturn(0L);
        when(queries.countRefundRetryExhausted()).thenReturn(0L);
        when(dedup.reserve(anyString(), any(Duration.class))).thenReturn(true);
        when(notifier.send(anyString())).thenReturn(new AlertNotifier.DeliveryResult(true, null));

        new PaymentAlertJob(properties, queries, notifier, publisher, dedup).evaluate();

        verify(publisher).publish(
                "PAYMENT_SETTLEMENT_UNDELIVERED", "HIGH",
                "支付成功但结算事件未投递成功 1 笔", true, null);
    }
}
