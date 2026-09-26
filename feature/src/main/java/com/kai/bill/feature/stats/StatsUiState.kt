package com.kai.bill.feature.stats

import com.kai.bill.domain.model.Bill
import com.kai.bill.domain.model.BillFilter
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.stats.AccountStat
import com.kai.bill.domain.model.stats.CategoryStat
import com.kai.bill.domain.model.stats.CategoryStatsMode
import com.kai.bill.domain.model.stats.Overview
import com.kai.bill.domain.model.stats.TrendPoint
import com.kai.bill.feature.common.DateRangeKind

/**
 * 周对比柱状图上的一个数据桶。
 *
 * @property startMillis 该周起始毫秒（周一 00:00）
 * @property endMillis 该周结束毫秒（周日 23:59:59.999）
 * @property amountCents 该周金额合计，单位「分」
 * @property label 展示文案，如 "8/17-8/23 第4周"（周数按自然月重置）
 */
data class WeeklyBar(
    val startMillis: Long,
    val endMillis: Long,
    val amountCents: Long,
    val label: String
)

/**
 * 统计页 UI 状态。
 *
 * 数据来源分成两组，**受不同的条件影响**：
 * - 统计类（[overview] / [categoryStats] / [trendPoints] / [accountStats]）：
 *   受 [rangeKind]、[statType] 与 [filter] 共同影响。无筛选时走 `StatsDao` 的 SQL 聚合，
 *   带筛选时改走明细的内存聚合（多选筛选无法用静态 SQL 优雅表达），两者口径一致。
 * - [bills]：受 [rangeKind] 与 [filter] 影响，走 `BillRepository.observeBills`。
 *
 * @property statType 统计维度：支出或收入；决定饼图与趋势统计哪一侧
 * @property categoryStats 分类统计，按金额降序，供饼图与排行共用
 * @property trendPoints 趋势点；年按月、日/周/月按日聚合
 * @property accountStats 账户维度统计，含「未指定账户」兜底项
 * @property filter 当前筛选条件；null 表示不筛选
 */
data class StatsUiState(
    val rangeKind: DateRangeKind = DateRangeKind.MONTH,
    val selectedDate: Long = 0L,
    val statType: BillType = BillType.EXPENSE,
    val categoryStatsMode: CategoryStatsMode = CategoryStatsMode.PARENT,
    val overview: Overview = Overview.EMPTY,
    val categoryStats: List<CategoryStat> = emptyList(),
    val trendPoints: List<TrendPoint> = emptyList(),
    val weeklyBars: List<WeeklyBar> = emptyList(),
    val accountStats: List<AccountStat> = emptyList(),
    val filter: BillFilter? = null,
    val bills: List<Bill> = emptyList()
) {

    /**
     * 本区间是否有流水。
     *
     * 空区间一律走空态：画一张全是 0 的饼图或空坐标轴，
     * 比明确告诉用户「这段时间还没有记账」更让人困惑。
     */
    val hasStatsData: Boolean
        get() = !overview.isEmpty

    /** 是否处于筛选状态（用于筛选入口的角标与已选条件摘要条） */
    val isFiltering: Boolean
        get() = filter != null
}
