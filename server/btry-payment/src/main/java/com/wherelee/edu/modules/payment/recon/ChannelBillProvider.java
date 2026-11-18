package com.wherelee.edu.modules.payment.recon;

import java.time.LocalDate;
import java.util.List;

/**
 * 渠道账单数据源端口。
 *
 * <p>对账服务不关心账单从哪来：支付宝是下载账单文件、微信是接口拉取、Mock 是从支付尝试表推导。
 * 新增渠道只需实现这个接口，对账核心一行不改。</p>
 */
public interface ChannelBillProvider {

    boolean supports(String channel);

    List<ChannelBillLine> fetch(String channel, LocalDate billDate);

    /** Mock 渠道：用「支付尝试表里渠道回报的结果」作为渠道侧账单。 */
    String CHANNEL_MOCK = "MOCK";
}
