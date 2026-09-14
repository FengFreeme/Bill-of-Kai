package com.kai.bill.domain.model.stats

/**
 * 分类维度的聚合中间结果：只有 ID 与金额，**不含分类名与颜色**。
 *
 * 之所以拆成中间模型：SQL 的 `GROUP BY categoryId` 只能拿到 ID，
 * 而名称与颜色在分类表里。由 `ObserveCategoryStatsUseCase` 再 `combine`
 * 分类流补齐 —— 这样 `data` 层不必同时依赖统计与分类两个 Dao，
 * 也避免逐条 `getById` 退化成 N+1 查询。
 *
 * @property categoryId 分类 ID
 * @property amountCents 该分类金额合计，单位「分」，非负
 * @property billCount 该分类的账单笔数
 */
data class CategoryAmount(
    val categoryId: Long,
    val amountCents: Long,
    val billCount: Int
)
