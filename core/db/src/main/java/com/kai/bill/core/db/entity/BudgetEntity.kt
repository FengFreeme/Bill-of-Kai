package com.kai.bill.core.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 预算表。
 *
 * @property categoryId 分类 ID；**为 null 表示总额预算**。预留分类预算，将来支持时无需改表
 * @property period 预算周期，当前仅 [BudgetPeriod.MONTHLY]
 * @property amountCents 月预算金额，单位「分」；0 表示未设置预算（首页不渲染预算卡）
 * @property startDay 周期起始日，取值 1~31，用于「发薪日起算」；当月不足该天数时退化为月末
 * @property dailyMode 日预算模式：弹性 / 固定
 * @property dailyAmountCents 固定日预算金额，单位「分」；仅 [DailyMode.FIXED] 生效
 * @property carryOver 是否把本月结余结转到下月
 * @property enabled 是否启用；关闭后首页不渲染预算卡，但保留历史设置
 */
@Entity(
    tableName = "budget",
    indices = [Index("categoryId")]
)
data class BudgetEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val categoryId: Long?,

    val period: BudgetPeriod,

    val amountCents: Long,

    val startDay: Int,

    val dailyMode: DailyMode,

    val dailyAmountCents: Long,

    val carryOver: Boolean,

    val enabled: Boolean
)
