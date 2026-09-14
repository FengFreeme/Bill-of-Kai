package com.kai.bill.domain.model.stats

/**
 * 单个分类的统计项，用于饼图与分类排行。
 *
 * @property categoryId 分类 ID
 * @property categoryName 分类名；由 `data` 层关联分类表补齐（SQL 聚合只返回 ID）
 * @property colorHex 分类色，形如 `#F2775F`；同样是关联补齐的，用于饼图扇区与色点
 * @property amountCents 该分类金额合计，单位「分」，非负
 * @property ratio 占同区间总额的比率，取值 0f~1f。**注意这是比率而非金额，
 *                 因此用 Float 不违反「金额禁止浮点」的约定**
 * @property billCount 该分类的账单笔数
 */
data class CategoryStat(
    val categoryId: Long,
    val categoryName: String,
    val colorHex: String,
    val amountCents: Long,
    val ratio: Float,
    val billCount: Int
)
