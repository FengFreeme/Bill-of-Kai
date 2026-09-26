package com.kai.bill.domain.calculator

import com.kai.bill.domain.model.Budget
import com.kai.bill.domain.model.DailyMode
import com.kai.bill.domain.model.stats.BudgetProgress

/**
 * 预算计算器 —— **纯函数**，无 Android 依赖、无 I/O、无状态，可直接在 JVM 上单测。
 *
 * 框架文档 §8.3 的算法：
 * ```
 * monthRemaining  = monthlyBudget - monthSpent
 * daysRemaining   = 本月剩余天数（含今天，月末最后一天为 1）
 * todayAvailable  = monthRemaining / daysRemaining      // 弹性模式
 * todayAvailable  = dailyBudget                          // 固定模式
 * todayLeft       = todayAvailable - todaySpent
 * ```
 *
 * **调用方的前置责任**：传入的 `monthSpentCents` / `todaySpentCents` 必须已经过滤掉
 * `countInStats == false` 的账单（转账、还款、待报销），否则预算会被这些数据污染。
 * 本计算器只做算术，不做数据筛选 —— 混进来什么就算什么。
 */
object BudgetCalculator {

    /**
     * 计算本月预算进度与今日可用额度。
     *
     * 支持两种日预算模式：
     * - [DailyMode.ELASTIC]：今日可用 = 本月剩余 ÷ 本月剩余天数（**推荐**）
     *   昨天省下的钱会自动补给今天，符合「心理账户」直觉
     * - [DailyMode.FIXED]：今日可用 = 固定日预算，与月预算互不影响
     *
     * @param monthlyBudgetCents 月预算金额，单位「分」，非负；**0 表示用户未设置预算**
     * @param monthSpentCents 本月已支出，单位「分」，非负
     * @param todaySpentCents 今日已支出，单位「分」，非负
     * @param daysRemaining 本月剩余天数（含今天），最小值为 1
     * @param dailyBudgetCents 固定日预算金额，单位「分」，非负；
     *                         [DailyMode.ELASTIC] 下本参数被忽略，传 0 即可
     * @return 预算进度快照，含已用 / 剩余 / 今日可用 / 是否超支
     *
     * @throws IllegalArgumentException 当任一金额为负数，或 [daysRemaining] 小于 1 时抛出
     *
     * @see DailyMode
     */
    fun calculate(
        budget: Budget,
        monthlyBudgetCents: Long,
        monthSpentCents: Long,
        todaySpentCents: Long,
        daysRemaining: Int,
        dailyMode: DailyMode,
        dailyBudgetCents: Long
    ): BudgetProgress {
        require(monthlyBudgetCents >= 0) { "月预算不能为负数，收到：$monthlyBudgetCents" }
        require(monthSpentCents >= 0) { "本月已支出不能为负数，收到：$monthSpentCents" }
        require(todaySpentCents >= 0) { "今日已支出不能为负数，收到：$todaySpentCents" }
        require(dailyBudgetCents >= 0) { "固定日预算不能为负数，收到：$dailyBudgetCents" }
        require(daysRemaining >= 1) { "本月剩余天数最小为 1，收到：$daysRemaining" }

        if (monthlyBudgetCents == 0L) {
            // 未设置预算：直接给一份全零快照。
            // 若不提前返回，monthRemaining 会算成「0 - 已花」= 负数，进而触发超支的红色警告 ——
            // 对一个从没设过预算的用户来说，这个警告既没意义也吓人
            val todayAvailableCents = when (dailyMode) {
                DailyMode.ELASTIC -> 0L
                DailyMode.FIXED -> dailyBudgetCents
            }
            return BudgetProgress(
                budget = budget,
                monthlyBudgetCents = 0L,
                monthSpentCents = monthSpentCents,
                monthRemainingCents = 0L,
                daysRemaining = daysRemaining,
                todayAvailableCents = todayAvailableCents,
                todaySpentCents = todaySpentCents,
                todayLeftCents = todayAvailableCents - todaySpentCents,
                progress = 0f,
                isOverBudget = false
            )
        }

        val monthRemainingCents = monthlyBudgetCents - monthSpentCents
        val isOverBudget = monthRemainingCents < 0

        // ELASTIC 下今天可能拿到负数额度（说明已超支），这是刻意的：
        // 与其把额度钳到 0 让用户以为「还能花」，不如如实显示负数
        val todayAvailableCents = when (dailyMode) {
            DailyMode.ELASTIC -> monthRemainingCents / daysRemaining
            DailyMode.FIXED -> dailyBudgetCents
        }

        // 比例钳到 1f：进度条画不满一整格会让人以为还有余量
        val progress = (monthSpentCents.toFloat() / monthlyBudgetCents.toFloat()).coerceIn(0f, 1f)

        return BudgetProgress(
            budget = budget,
            monthlyBudgetCents = monthlyBudgetCents,
            monthSpentCents = monthSpentCents,
            monthRemainingCents = monthRemainingCents,
            daysRemaining = daysRemaining,
            todayAvailableCents = todayAvailableCents,
            todaySpentCents = todaySpentCents,
            todayLeftCents = todayAvailableCents - todaySpentCents,
            progress = progress,
            isOverBudget = isOverBudget
        )
    }
}
