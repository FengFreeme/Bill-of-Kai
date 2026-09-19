package com.kai.bill.feature.stats

import com.kai.bill.domain.model.BillFilter
import com.kai.bill.domain.model.BillType

/**
 * 筛选面板的**草稿**状态。
 *
 * 与已生效的 [BillFilter] 分开：面板里勾选后要点「查看结果」才真正应用，
 * 否则每勾一下就重查一次数据库，既浪费又会让列表在手指底下乱跳。
 *
 * 打开面板时由 `StatsViewModel.openFilterPanel` 重置为**什么都不勾**（分类 / 账户 /
 * 「未指定账户」全不选），也不回填当前生效的筛选 —— 勾什么完全由用户自己决定。
 * 生效中的条件在页面摘要条上，可一键清除。
 *
 * @property type 账单类型；null 为「不限」；面板里它跟随统计页维度，不算用户勾的条件
 * @property categoryIds 选中的分类 ID；**空集合表示不限**（不是「什么都不匹配」）
 * @property accountIds 选中的账户 ID；**空集合表示不限**（同上）
 * @property includeUnspecifiedAccount 是否包含「未指定账户」；账户一个不勾时由它单独决定要不要限定
 */
data class StatsFilterDraft(
    val type: BillType? = null,
    val categoryIds: Set<Long> = emptySet(),
    val accountIds: Set<Long> = emptySet(),
    val includeUnspecifiedAccount: Boolean = false
) {

    /** 是否一个条件都没选（用于「重置」按钮置灰与提交时判断） */
    val isEmpty: Boolean
        get() = type == null &&
            categoryIds.isEmpty() &&
            accountIds.isEmpty() &&
            !includeUnspecifiedAccount

    /**
     * 转成生效的筛选条件。
     *
     * @return 无任何条件时返回 null（等价不筛选），避免构造出一个「全空但非 null」的
     *         filter 让下游误以为在筛选
     */
    fun toFilter(): BillFilter? =
        if (isEmpty) {
            null
        } else {
            BillFilter(
                type = type,
                categoryIds = categoryIds,
                accountIds = accountIds,
                includeUnspecifiedAccount = includeUnspecifiedAccount,
                // 统计页的流水与统计口径保持一致：转账还款不出现在列表里
                countInStats = true
            )
        }
}
