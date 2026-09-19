package com.kai.bill.domain.model

/**
 * 账单的组合筛选条件。
 *
 * **空集合表示「不按该维度筛选」**，与 null 一样收口成「不限」。
 * 之所以用集合而不是单值：统计页的筛选面板支持分类 / 账户多选，
 * 单值模型只能表达「选中一个」，多选需求会逼 UI 退化成单选。
 *
 * @property type 账单类型；null 表示不过滤类型
 * @property categoryIds 分类 ID 集合；**空集合表示不过滤分类**
 * @property accountIds 账户 ID 集合；**空集合表示不过滤账户**
 * @property includeUnspecifiedAccount 是否包含「未指定账户」（accountId 为 null）的流水。
 *           与 [accountIds] 分开表达：null 是一个合法的账户取值，
 *           把它塞进 `Set<Long>` 会丢失语义 —— 无法区分
 *           「没选账户」和「专门选了未指定账户」
 * @property countInStats 是否计入统计；null 表示不过滤。
 *           注意传 true 与传 false 是两种不同意图：前者「只看计入统计的」，
 *           后者「只看转账还款」
 */
data class BillFilter(
    val type: BillType? = null,
    val categoryIds: Set<Long> = emptySet(),
    val accountIds: Set<Long> = emptySet(),
    val includeUnspecifiedAccount: Boolean = false,
    val countInStats: Boolean? = null
)

/**
 * 换算成「该筛选自身维度下真正生效」的条件。
 *
 * 转账维度要放开 [BillFilter.countInStats]：那个开关对转账本就是空操作
 * （[Bill.isCounted] 已把转账排除在收支之外），但筛选面板默认会带上 `countInStats = true`，
 * 于是转账会被顺手挡在明细查询之外，转账维度一片空。
 *
 * 支出 / 收入维度原样返回 —— 它们的口径就是「只统计计入统计的流水」。
 */
fun BillFilter.effectiveForDimension(): BillFilter =
    if (type == BillType.TRANSFER) copy(countInStats = null) else this
