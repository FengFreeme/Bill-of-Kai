package com.kai.bill.domain.model

/**
 * 账户类型。
 *
 * 只描述「资金载体」，不参与统计口径 —— 统计的两个维度是分类与账户，
 * 账户维度统计按 [Account.id] 聚合，与本枚举无关。
 */
enum class AccountType {

    /** 现金 */
    CASH,

    /** 支付宝 */
    ALIPAY,

    /** 微信支付 */
    WECHAT,

    /** 储蓄卡 */
    BANK_CARD,

    /** 信用卡 */
    CREDIT_CARD
}
