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
