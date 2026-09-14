package com.kai.bill.core.db.model

/**
 * 按账户聚合的小计行（M2 的账户维度统计）。
 *
 * @property accountId 账户 ID；为 null 表示「未指定账户」的流水也要单独归一组
 * @property totalCents 该账户金额小计，单位「分」，非负
 */
data class AccountTotalRow(
    val accountId: Long?,
    val totalCents: Long
)
