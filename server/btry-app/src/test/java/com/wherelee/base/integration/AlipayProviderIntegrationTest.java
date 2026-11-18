package com.wherelee.base.integration;

import com.alipay.api.internal.util.AlipaySignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 真实支付宝链路的"上下文级"证据：provider=alipay 时装配真实的 AlipayClient/AlipayNotifyService，
 * 用真实 RSA2 签名报文走一遍 {@code POST /api/payments/alipay/notify}，不访问外网、不需要公网域名。
 */
@Tag("integration")
@AutoConfigureMockMvc
class AlipayProviderIntegrationTest extends KernelIntegrationTestSupport {

    private static final String APP_ID = "2021000000000000";
    private static final String SELLER_ID = "2088000000000000";
    private static final String OUT_TRADE_NO = "IT-ORD1_1";
    private static final String PRIVATE_KEY;
    private static final String PUBLIC_KEY;

    static {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            PRIVATE_KEY = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
            PUBLIC_KEY = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException("生成测试 RSA 密钥失败", e);
        }
    }

    @DynamicPropertySource
    static void alipayProps(DynamicPropertyRegistry registry) {
        registry.add("app.payment.provider", () -> "alipay");
        registry.add("app.payment.app-id", () -> "alipay-it");
        registry.add("app.payment.query.enabled", () -> "false");
        registry.add("app.payment.alipay.app-id", () -> APP_ID);
        registry.add("app.payment.alipay.private-key", () -> PRIVATE_KEY);
        registry.add("app.payment.alipay.alipay-public-key", () -> PUBLIC_KEY);
        registry.add("app.payment.alipay.seller-id", () -> SELLER_ID);
        registry.add("app.payment.alipay.gateway-url", () -> "https://openapi.alipay.com/gateway.do");
        registry.add("app.payment.alipay.notify-url", () -> "");
        registry.add("app.payment.alipay.return-url", () -> "");
        registry.add("app.payment.alipay.bill.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("provider=alipay：真实 RSA2 签名的异步通知能把支付单确认成 PAID 并写发件箱")
    void signedNotifyConfirmsPaymentAndWritesOutbox() throws Exception {
        seedPayment();

        mockMvc.perform(post("/api/payments/alipay/notify")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content(signedNotify("TRADE_SUCCESS")))
                .andExpect(status().isOk())
                .andExpect(content().string("success"));

        assertThat(jdbc.queryForObject(
                "SELECT status FROM t_payment_order WHERE payment_no = 'P-IT-ORD1'", String.class))
                .isEqualTo("PAID");
        assertThat(jdbc.queryForObject(
                "SELECT provider_trade_no FROM t_payment_order WHERE payment_no = 'P-IT-ORD1'", String.class))
                .isEqualTo("2026110522001400001");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_payment_outbox WHERE event_type = 'PAYMENT_SUCCEEDED' "
                        + "AND aggregate_no = 'P-IT-ORD1'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("provider=alipay：验签失败的异步通知被拒绝且不改支付单")
    void tamperedNotifyIsRejected() throws Exception {
        seedPayment();
        String body = signedNotify("TRADE_SUCCESS").replace("total_amount=0.01", "total_amount=9.99");

        mockMvc.perform(post("/api/payments/alipay/notify")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string("failure"));

        assertThat(jdbc.queryForObject(
                "SELECT status FROM t_payment_order WHERE payment_no = 'P-IT-ORD1'", String.class))
                .isEqualTo("PAYING");
    }

    private void seedPayment() {
        jdbc.update("""
                INSERT INTO t_payment_order
                    (payment_no, app_id, biz_type, biz_no, tenant_id, subject, amount_cents, currency,
                     status, channel, provider_trade_no, expire_at, source_updated_at)
                VALUES ('P-IT-ORD1', 'alipay-it', 'IT_ORDER', 'IT-ORD1', 0, '示例订单', 1, 'CNY',
                        'PAYING', 'ALIPAY', ?, DATE_ADD(NOW(3), INTERVAL 15 MINUTE), NOW(3))
                """, OUT_TRADE_NO);
        jdbc.update("""
                INSERT INTO t_payment_attempt
                    (tenant_id, payment_no, attempt_no, channel, status, provider_trade_no,
                     provider_status, provider_amount_cents, request_digest)
                VALUES (0, 'P-IT-ORD1', 1, 'ALIPAY', 'PROCESSING', ?, 'CREATED', 1, 'digest')
                """, OUT_TRADE_NO);
    }

    private String signedNotify(String tradeStatus) throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("notify_time", "2026-11-19 01:00:00");
        params.put("notify_type", "trade_status_sync");
        params.put("notify_id", "IT-NOTIFY-1");
        params.put("app_id", APP_ID);
        params.put("charset", "UTF-8");
        params.put("version", "1.0");
        params.put("sign_type", "RSA2");
        params.put("out_trade_no", OUT_TRADE_NO);
        params.put("trade_no", "2026110522001400001");
        params.put("trade_status", tradeStatus);
        params.put("total_amount", "0.01");
        params.put("seller_id", SELLER_ID);
        params.put("subject", "示例订单");
        String content = AlipaySignature.getSignCheckContentV1(params);
        String sign = AlipaySignature.rsaSign(content, PRIVATE_KEY, "UTF-8", "RSA2");
        params.put("sign", sign);
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                    .append('&');
        }
        return builder.toString();
    }
}
