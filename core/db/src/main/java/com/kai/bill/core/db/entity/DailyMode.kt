package com.kai.bill.core.db.entity

/**
 * 日预算计算模式（数据库存储形态）。
 *
 * 见框架文档 §8.3：
 * - [ELASTIC]：今日可用 = 本月剩余 ÷ 本月剩余天数，昨天省下的钱自动摊到今天
 * - [FIXED]：今日可用 = 固定日预算，与月预算互不影响
 */
enum class DailyMode {

    /** 弹性日预算（默认） */
    ELASTIC,

    /** 固定日预算 */
    FIXED
}
