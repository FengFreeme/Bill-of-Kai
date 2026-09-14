package com.kai.bill.domain.model.stats

/**
 * 统计粒度：日 / 月 / 年。
 *
 * 定义在 domain 而非 UI 层：`ObserveTrendUseCase` 需要据此决定
 * 走「按日聚合」还是「按月聚合」的查询，UI 的 `DateRangeKind` 只是它的展示映射。
 *
 * 粒度与聚合方式的关系：
 * - [DAY] / [MONTH]：按自然日聚合（`observeDailyTrend`）
 * - [YEAR]：按自然月聚合（`observeMonthlyTrend`），
 *   365 根日柱既画不下也没有阅读价值
 */
enum class StatsGranularity {
    DAY,
    MONTH,
    YEAR
}
