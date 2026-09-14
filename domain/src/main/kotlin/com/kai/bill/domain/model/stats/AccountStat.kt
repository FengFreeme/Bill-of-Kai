package com.kai.bill.domain.model.stats

/**
 * 单个账户的统计项，用于统计页的账户维度区块。
 *
 * @property accountId 账户 ID；null 为「未指定账户」
 * @property accountName 账户名；由 `ObserveAccountStatsUseCase` 关联账户表补齐，
 *                       账户已被删除时兜底为「已删除账户」
 * @property amountCents 该账户金额合计，单位「分」，非负
 * @property ratio 占同区间同类型总额的比率，取值 0f~1f。
 *                 **注意这是比率而非金额，用 Float 不违反「金额禁止浮点」的约定**
 */
data class AccountStat(
    val accountId: Long?,
    val accountName: String,
    val amountCents: Long,
    val ratio: Float
)
