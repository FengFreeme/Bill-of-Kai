package com.kai.bill.domain.calculator

import com.google.common.truth.Truth.assertThat
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

    // ---------- 弹性模式 ----------

    @Test
    fun calculate_splitsRemainingAcrossDays_whenElasticMode() {
        // Given：月预算 3000 元，已花 1000 元，还剩 10 天（含今天）
        // When
        val progress = BudgetCalculator.calculate(
            monthlyBudgetCents = 300_000L,
            monthSpentCents = 100_000L,
            todaySpentCents = 2_000L,
            daysRemaining = 10,
            dailyMode = DailyMode.ELASTIC,
            dailyBudgetCents = 0L
        )

        // Then：剩余 2000 元 ÷ 10 天 = 今日可用 200 元
        assertThat(progress.monthRemainingCents).isEqualTo(200_000L)
        assertThat(progress.todayAvailableCents).isEqualTo(20_000L)
        assertThat(progress.todayLeftCents).isEqualTo(18_000L)
        assertThat(progress.isOverBudget).isFalse()
        assertThat(progress.progress).isWithin(0.001f).of(0.3333f)
    }

    @Test
    fun calculate_givesAllRemainingToToday_whenLastDayOfMonth() {
        // Given：月末最后一天，剩余天数恒为 1
        val progress = BudgetCalculator.calculate(
            monthlyBudgetCents = 100_000L,
            monthSpentCents = 40_000L,
            todaySpentCents = 0L,
            daysRemaining = 1,
            dailyMode = DailyMode.ELASTIC,
            dailyBudgetCents = 0L
        )

        // Then：全部月剩余都给今天，而不是「剩余 ÷ 1」之外的任何值
        assertThat(progress.monthRemainingCents).isEqualTo(60_000L)
        assertThat(progress.todayAvailableCents).isEqualTo(60_000L)
        assertThat(progress.todayLeftCents).isEqualTo(60_000L)
    }

    // ---------- 超支 ----------

    @Test
    fun calculate_returnsNegativeRemainingAndMarksOverBudget_whenSpentExceedsBudget() {
        val progress = BudgetCalculator.calculate(
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
}
