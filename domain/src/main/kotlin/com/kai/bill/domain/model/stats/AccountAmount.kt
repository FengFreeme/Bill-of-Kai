package com.kai.bill.domain.model.stats

/**
 * 账户维度的聚合中间结果。
 *
 * @property accountId 账户 ID；**null 表示「未指定账户」的流水**，
 *                     统计时要保留这一项，否则这部分金额会凭空消失
 * @property amountCents 该账户金额合计，单位「分」，非负
 */
data class AccountAmount(
    val accountId: Long?,
    val amountCents: Long
)
