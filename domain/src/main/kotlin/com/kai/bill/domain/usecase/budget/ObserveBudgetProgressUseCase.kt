package com.kai.bill.domain.usecase.budget

import com.kai.bill.domain.calculator.BudgetCalculator
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.Budget
import com.kai.bill.domain.model.stats.BudgetProgress
import com.kai.bill.domain.model.stats.CategoryAmount
import com.kai.bill.domain.repository.BudgetRepository
import com.kai.bill.domain.repository.StatsRepository
import com.kai.bill.domain.time.MonthDayRanges
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * 把「预算配置流」和「实际支出聚合」组合成 UI 可直接消费的 [BudgetProgress] 列表。
 *
 * 组合维度：
 * - 预算配置：[BudgetRepository.observeEnabled]（已启用预算，含总额与分类预算）
 * - 支出聚合：[StatsRepository.observeOverview]（总额）+ [StatsRepository.observeCategoryAmounts]
 *   （分类，用于分类预算的支出归口）
 * - 时间区间：[MonthDayRanges] 由 [com.kai.bill.domain.time.Clock] 推导，不硬编码
 *
 * 每个 budget 经 [BudgetCalculator.calculate] 算出进度快照；返回的列表可直接驱动
 * 预算页的进度条 / 超支提醒，无需 UI 关心统计口径。
 *
 * 注意：月 / 日区间在订阅时由 [MonthDayRanges] 推导一次，跨月 / 跨日不会自动重算，
 * 直到任意一条源流（账单变化）再次发射。个人记账场景这是可接受的简化。
 */
class ObserveBudgetProgressUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val statsRepository: StatsRepository,
    private val ranges: MonthDayRanges
) {

    operator fun invoke(): Flow<List<BudgetProgress>> =
        combine(
            budgetRepository.observeEnabled(),
            statsRepository.observeOverview(ranges.currentMonth()),
            statsRepository.observeOverview(ranges.today()),
            statsRepository.observeCategoryAmounts(ranges.currentMonth(), BillType.EXPENSE),
            statsRepository.observeCategoryAmounts(ranges.today(), BillType.EXPENSE)
        ) { budgets, monthOverview, todayOverview, monthCat, todayCat ->
            val daysRemaining = ranges.daysRemainingInMonth()
            budgets.map { budget ->
                val monthSpent = spentFor(budget, monthOverview.expenseCents, monthCat)
                val todaySpent = spentFor(budget, todayOverview.expenseCents, todayCat)
                BudgetCalculator.calculate(
                    budget = budget,
                    monthlyBudgetCents = budget.amountCents,
                    monthSpentCents = monthSpent,
                    todaySpentCents = todaySpent,
                    daysRemaining = daysRemaining,
                    dailyMode = budget.dailyMode,
                    dailyBudgetCents = budget.dailyAmountCents
                )
            }
        }

    /**
     * 取某预算的「已支出」：
     * - 总额预算：直接取区间总支出（已按 `countInStats` 过滤，转账还款不计入）
     * - 分类预算：从分类聚合里挑出对应分类的金额
     */
    private fun spentFor(
        budget: Budget,
        totalExpenseCents: Long,
        categoryAmounts: List<CategoryAmount>
    ): Long = if (budget.isTotalBudget) {
        totalExpenseCents
    } else {
        categoryAmounts.firstOrNull { it.categoryId == budget.categoryId }?.amountCents ?: 0L
    }
}
