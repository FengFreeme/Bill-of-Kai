package com.kai.bill.core.db.entity

/**
 * 账单来源（数据库存储形态）。
 *
 * 区分来源的意义：解析失败时可按来源定位是「通知改版」还是「短信模板变化」，
 * 另外 [MANUAL] 的数据可信度更高，去重时可以放宽时间窗。
 */
enum class SourceType {

    /** 通知监听捕获（支付宝 / 微信 / 银行 App 的支付通知） */
    NOTIFICATION,

    /** 短信解析（银行交易短信） */
    SMS,

    /** 用户手动记账 */
    MANUAL
}
