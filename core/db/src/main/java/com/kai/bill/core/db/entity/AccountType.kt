package com.kai.bill.core.db.entity

/**
 * 账户类型（数据库存储形态）。
 *
 * 只区分「资金载体」，不参与统计口径 —— 统计维度是分类 + 账户两个独立维度，
 * 账户维度统计按 [com.kai.bill.core.db.entity.AccountEntity.id] 聚合，与本枚举无关。
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
