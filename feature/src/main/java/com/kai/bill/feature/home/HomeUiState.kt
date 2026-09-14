package com.kai.bill.feature.home

import com.kai.bill.domain.model.Account
import com.kai.bill.domain.model.Bill
import com.kai.bill.domain.model.Category
import com.kai.bill.domain.model.stats.BudgetProgress
import java.time.LocalDate

/**
 * 首页 UI 状态。
 *
 * - [overview] 为累计 / 本月支出 / 本月收入等聚合数据；
 * - [dailyGroups] 为按交易日分组的流水，用于消费记录列表；
 * - [budgetProgress] 为预算进度；null 时不展示预算卡；
 * - [categories] / [accounts] 用于把分类 ID / 账户 ID 渲染成可读的图标与名称；
 * - [todayLabel] 为顶栏「今日」后的日期文案（如「9月14日」）。
 */
data class HomeUiState(
    val overview: HomeOverview = HomeOverview(),
    val budgetProgress: BudgetProgress? = null,
    val dailyGroups: List<DailyGroup> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val todayLabel: String = ""
)

/**
 * 首页顶部概览数据。
 *
 * @property totalExpenseCents 累计消费（支出合计，分）
 * @property monthExpenseCents 本月支出（分）
 * @property monthIncomeCents 本月收入（分）
 * @property todayExpenseCents 今日支出（分）
 */
data class HomeOverview(
    val totalExpenseCents: Long = 0,
    val monthExpenseCents: Long = 0,
    val monthIncomeCents: Long = 0,
    val todayExpenseCents: Long = 0
) {

    /**
     * 本月结余 = 本月收入 - 本月支出；为负表示入不敷出。
     *
     * 做成计算属性而非构造参数：结余恒等于两者之差，允许外部传入就可能出现
     * 三个字段互相矛盾的状态。
     */
    val monthBalanceCents: Long
        get() = monthIncomeCents - monthExpenseCents
}

/**
 * 单日流水组。
 *
 * @property date 交易日
 * @property dateLabel 如「9月12日 今日」
 * @property expenseCents 当日支出合计
 * @property incomeCents 当日收入合计
 * @property bills 当日流水，按交易时间倒序
 */
data class DailyGroup(
    val date: LocalDate,
    val dateLabel: String,
    val expenseCents: Long,
    val incomeCents: Long,
    val bills: List<Bill>
)
