package com.wherelee.base.modules.payment.alipay.bill;

import com.wherelee.base.modules.payment.recon.ChannelBillLine;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class AlipayBillParserTest {

    private static final String CSV = """
            支付宝交易记录明细查询
            账号:[2088000000000000]
            起始日期:[2026-11-01 00:00:00]    终止日期:[2026-11-01 23:59:59]
            ------------------------------------------交易明细列表------------------------------------
            支付宝交易号,商户订单号,业务类型,商品名称,创建时间,完成时间,门店编号,门店名称,操作员,终端号,对方账户,订单金额（元）,商家实收（元）,支付宝红包（元）,集分宝（元）,支付宝优惠（元）,商家优惠（元）,券核销金额（元）,券名称,商家红包消费金额（元）,卡消费金额（元）,退款批次号/请求号,服务费（元）,分润（元）,备注
            2026110522001400001,ORD1_1,交易,示例商品,2026-11-01 10:00:00,2026-11-01 10:01:00,,,,,buyer1@example.com,199.00,198.81,0.00,0.00,0.00,0.19,0.00,,0.00,0.00,,0.19,0.00,
            2026110522001400002,ORD2_1,交易,"示例,商品",2026-11-01 11:00:00,2026-11-01 11:01:00,,,,,buyer2@example.com,99.50,99.33,0.00,0.00,0.00,0.17,0.00,,0.00,0.00,,0.17,0.00,
            2026110522001400003,ORD3_1,退款,示例商品,2026-11-01 12:00:00,2026-11-01 12:01:00,,,,,buyer3@example.com,50.00,49.95,0.00,0.00,0.00,0.05,0.00,,0.00,0.00,REF-1,0.05,0.00,
            ,,,,,,,,,,,,,,,,,,,,,,
            共 3 笔记录
            已支出金额: 348.50 元;已收入金额: 348.50 元
            """;

    @Test
    void parsesPaidLinesAndSkipsRefundsAndFooter() {
        List<ChannelBillLine> lines = AlipayBillParser.parse(CSV.getBytes(StandardCharsets.UTF_8), "UTF-8");

        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).providerTradeNo()).isEqualTo("ORD1_1");
        assertThat(lines.get(0).amountCents()).isEqualTo(19900L);
        assertThat(lines.get(0).status()).isEqualTo(ChannelBillLine.STATUS_PAID);
        assertThat(lines.get(0).paidAt()).isNotNull();
        assertThat(lines.get(1).providerTradeNo()).isEqualTo("ORD2_1");
        assertThat(lines.get(1).amountCents()).isEqualTo(9950L);
    }

    @Test
    void parsesZipBillInGbk() throws Exception {
        byte[] gbkCsv = CSV.getBytes(Charset.forName("GBK"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("2088000000000000_20261101.csv"));
            zip.write(gbkCsv);
            zip.closeEntry();
        }

        List<ChannelBillLine> lines = AlipayBillParser.parse(out.toByteArray(), "GBK");

        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).providerTradeNo()).isEqualTo("ORD1_1");
    }

    @Test
    void returnsEmptyWhenNoHeaderFound() {
        List<ChannelBillLine> lines = AlipayBillParser.parse(
                "任意非账单内容".getBytes(StandardCharsets.UTF_8), "UTF-8");
        assertThat(lines).isEmpty();
    }
}
