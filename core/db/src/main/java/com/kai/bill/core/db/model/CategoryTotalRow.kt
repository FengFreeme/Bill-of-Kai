package com.kai.bill.core.db.model

/**
 * 按分类聚合的小计行（分类排行 / 饼图）。
 *
 * @property categoryId 分类 ID
 * @property totalCents 该分类金额小计，单位「分」，非负
 * @property billCount 该分类的账单笔数；分类排行里「餐饮 · 12 笔」要用到
 */
data class CategoryTotalRow(
    val categoryId: Long,
    val totalCents: Long,
    val billCount: Int
)
