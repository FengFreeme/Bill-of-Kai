package com.kai.bill.domain.calculator

import com.google.common.truth.Truth.assertThat
import com.kai.bill.domain.model.Budget
import com.kai.bill.domain.model.BudgetPeriod
import com.kai.bill.domain.model.DailyMode
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * [BudgetCalculator] 的单测。
 *
 * 为什么值得为它写测试：预算算法是本项目**唯一容易算错又直接影响用户决策**的逻辑 ——
 * 算错一天，用户就会以为「还能花」而超支；而且它边界极多（超支、月末最后一天、
 * 未设预算），靠手点 UI 根本覆盖不全。纯函数形态让这些边界都能在 JVM 上钉死。
 */
class BudgetCalculatorTest {

    /** 总额预算配置：本文件的用例只关心纯计算，配置固定，模式与金额由参数显式传入 */
    private val totalBudget = budget()

    // ---------- 弹性模式 ----------

    @Test
    fun calculate_splitsRemainingAcrossDays_whenElasticMode() {
        val progress = BudgetCalculator.calculate(
            budget = totalBudget,
            monthlyBudgetCents = 300_000L,
            monthSpentCents = 100_000L,
            todaySpentCents = 2_000L,
            daysRemaining = 10,
            dailyMode = DailyMode.ELASTIC,
            dailyBudgetCents = 0L
        )

        assertThat(progress.monthRemainingCents).isEqualTo(200_000L)
        assertThat(progress.todayAvailableCents).isEqualTo(20_000L)
        assertThat(progress.todayLeftCents).isEqualTo(18_000L)
        assertThat(progress.isOverBudget).isFalse()
        assertThat(progress.progress).isWithin(0.001f).of(0.3333f)
        // 快照要把来源预算带出去：列表里「总额预算」与「分类预算」靠它区分
        assertThat(progress.budget).isEqualTo(totalBudget)
    }

    @Test
    fun calculate_givesAllRemainingToToday_whenLastDayOfMonth() {
        val progress = BudgetCalculator.calculate(
            budget = totalBudget,
            monthlyBudgetCents = 100_000L,
            monthSpentCents = 40_000L,
            todaySpentCents = 0L,
            daysRemaining = 1,
            dailyMode = DailyMode.ELASTIC,
            dailyBudgetCents = 0L
        )

        assertThat(progress.monthRemainingCents).isEqualTo(60_000L)
        assertThat(progress.todayAvailableCents).isEqualTo(60_000L)
        assertThat(progress.todayLeftCents).isEqualTo(60_000L)
    }

    // ---------- 超支 ----------

    @Test
    fun calculate_returnsNegativeRemainingAndMarksOverBudget_whenSpentExceedsBudget() {
        val progress = BudgetCalculator.calculate(
            budget = totalBudget,
            monthlyBudgetCents = 100_000L,
            monthSpentCents = 120_000L,
            todaySpentCents = 0L,
            daysRemaining = 5,
            dailyMode = DailyMode.ELASTIC,
            dailyBudgetCents = 0L
        )

        // 超支后今日可用也必须穿负：把额度钳到 0 会让用户误以为「今天还能花」
        assertThat(progress.monthRemainingCents).isEqualTo(-20_000L)
        assertThat(progress.todayAvailableCents).isEqualTo(-4_000L)
        assertThat(progress.todayLeftCents).isEqualTo(-4_000L)
        assertThat(progress.isOverBudget).isTrue()
    }

    @Test
    fun calculate_clampsProgressToOne_whenOverBudget() {
        val progress = BudgetCalculator.calculate(
            budget = totalBudget,
            monthlyBudgetCents = 100_000L,
            monthSpentCents = 120_000L,
            todaySpentCents = 0L,
            daysRemaining = 5,
            dailyMode = DailyMode.ELASTIC,
            dailyBudgetCents = 0L
        )

        // 原始比率是 1.2，但进度条最多只能画满一格
        assertThat(progress.progress).isEqualTo(1f)
    }

    // ---------- 固定模式 ----------

    @Test
    fun calculate_usesFixedDailyAmount_whenFixedMode() {
        val progress = BudgetCalculator.calculate(
            budget = budget(dailyMode = DailyMode.FIXED, dailyAmountCents = 10_000L),
            monthlyBudgetCents = 300_000L,
            monthSpentCents = 100_000L,
            todaySpentCents = 30_000L,
            daysRemaining = 10,
            dailyMode = DailyMode.FIXED,
            dailyBudgetCents = 10_000L
        )

        // 固定模式下今日可用与「月剩余 ÷ 天数」无关，恒等于日预算
        assertThat(progress.todayAvailableCents).isEqualTo(10_000L)
        assertThat(progress.todayLeftCents).isEqualTo(-20_000L)
        assertThat(progress.monthRemainingCents).isEqualTo(200_000L)
        assertThat(progress.progress).isWithin(0.001f).of(0.3333f)
    }

    // ---------- 未设置预算 ----------

    @Test
    fun calculate_returnsZeroedSnapshot_whenBudgetNotSet() {
        val progress = BudgetCalculator.calculate(
            budget = totalBudget,
            monthlyBudgetCents = 0L,
            monthSpentCents = 50_000L,
            todaySpentCents = 1_000L,
            daysRemaining = 10,
            dailyMode = DailyMode.ELASTIC,
            dailyBudgetCents = 0L
        )

        // 关键：不能因为「0 - 已花 < 0」就报超支，用户从没设过预算
        assertThat(progress.progress).isEqualTo(0f)
        assertThat(progress.isOverBudget).isFalse()
        assertThat(progress.monthRemainingCents).isEqualTo(0L)
        assertThat(progress.todayAvailableCents).isEqualTo(0L)
    }

    // ---------- 参数校验 ----------

    @Test
    fun calculate_throwsException_whenMonthlyBudgetIsNegative() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            BudgetCalculator.calculate(
                budget = totalBudget,
                monthlyBudgetCents = -1L,
                monthSpentCents = 0L,
                todaySpentCents = 0L,
                daysRemaining = 10,
                dailyMode = DailyMode.ELASTIC,
                dailyBudgetCents = 0L
            )
        }
        assertThat(error).hasMessageThat().contains("月预算")
    }

    @Test
    fun calculate_throwsException_whenSpentAmountIsNegative() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            BudgetCalculator.calculate(
                budget = totalBudget,
                monthlyBudgetCents = 100_000L,
                monthSpentCents = -1L,
                todaySpentCents = 0L,
                daysRemaining = 10,
                dailyMode = DailyMode.ELASTIC,
                dailyBudgetCents = 0L
            )
        }
        assertThat(error).hasMessageThat().contains("本月已支出")
    }

    @Test
    fun calculate_throwsException_whenDaysRemainingIsZero() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            BudgetCalculator.calculate(
                budget = totalBudget,
                monthlyBudgetCents = 100_000L,
                monthSpentCents = 0L,
                todaySpentCents = 0L,
                daysRemaining = 0,
                dailyMode = DailyMode.ELASTIC,
                dailyBudgetCents = 0L
            )
        }
        assertThat(error).hasMessageThat().contains("剩余天数")
    }

    /** 总额预算（`categoryId = null`）的最小配置；纯计算用例不关心其余字段 */
    private fun budget(
        dailyMode: DailyMode = DailyMode.ELASTIC,
        dailyAmountCents: Long = 0L
    ): Budget = Budget(
        id = 1L,
        categoryId = null,
        period = BudgetPeriod.MONTHLY,
        amountCents = 300_000L,
        startDay = 1,
        dailyMode = dailyMode,
        dailyAmountCents = dailyAmountCents,
        carryOver = false,
        enabled = true
    )
}
