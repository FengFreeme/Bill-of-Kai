package com.kai.bill.domain.model.stats

import com.kai.bill.domain.model.Budget

/**
 * 预算进度快照，预算卡的唯一数据源。
 *
 * 由 [com.kai.bill.domain.calculator.BudgetCalculator] 纯函数算出，
 * 不含任何 Android / 数据库依赖，因此可以直接在 JVM 上单测。
 *
 * 携带来源 [budget]：列表里同时有「总额预算」与「分类预算」时，
 * UI 靠 [Budget.categoryId] / [Budget.isTotalBudget] 区分并展示对应分类名。
 *
 * @property budget 本进度对应的预算配置（含分类、日预算模式等），来自 [com.kai.bill.domain.calculator.BudgetCalculator] 的入参透传
 * @property monthlyBudgetCents 月预算金额，单位「分」；0 表示用户未设置预算
 * @property monthSpentCents 本月已支出，单位「分」，非负
 * @property monthRemainingCents 本月剩余，单位「分」；**可以为负，负数即超支**
 * @property daysRemaining 本月剩余天数（含今天），最小为 1
 * @property todayAvailableCents 今日可用额度，单位「分」；超支时为负
 * @property todaySpentCents 今日已支出，单位「分」，非负
 * @property todayLeftCents 今日还可花多少，单位「分」；为负表示今天已超支
 * @property progress 已用比例，取值 0f~1f（超支时钳制为 1f）。**这是比率不是金额，用 Float 无精度问题**
 * @property isOverBudget 是否超支；UI 据此把进度条变红
 */
data class BudgetProgress(
    val budget: Budget,
    val monthlyBudgetCents: Long,
    val monthSpentCents: Long,
    val monthRemainingCents: Long,
    val daysRemaining: Int,
    val todayAvailableCents: Long,
    val todaySpentCents: Long,
    val todayLeftCents: Long,
    val progress: Float,
    val isOverBudget: Boolean
)
