package com.kai.bill.domain.model

/**
 * 账单来源。
 *
 * 区分来源有两个实际用途：
 * 1. 定位故障 —— 解析失败时可以区分是「通知文案改版」还是「短信模板变化」
 * 2. 调整去重策略 —— [MANUAL] 的可信度高，时间窗可以放宽；
 *    [NOTIFICATION] 与 [SMS] 可能重复到达同一笔消费，需要严格判重
 */
enum class SourceType {

    /** 通知监听捕获（支付宝 / 微信 / 银行 App 的支付通知） */
    NOTIFICATION,

    /** 短信解析（银行的交易短信） */
    SMS,

    /** 用户手动记账 */
    MANUAL
}
