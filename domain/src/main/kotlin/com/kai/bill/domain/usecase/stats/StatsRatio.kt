package com.kai.bill.domain.usecase.stats

import kotlin.math.abs

/**
 * 计算「部分占整体」的比率，取值 0f~1f；统计用例共用，保证饼图与排行榜分母一致。
 *
 * 分子取绝对值、分母要传 [absoluteTotalOf] 的结果（各项绝对值之和）。
 * 退款是支出侧的负项，用净额与带符号金额会算出负比率 —— 环形图按 `ratio * 360f`
 * 铺角度会得到负扇形，绘制与命中检测一起错位，排行榜也会被 `coerceIn` 夹成 0%。
 */
internal fun ratioOf(partCents: Long, totalCents: Long): Float =
    if (totalCents == 0L) 0f else abs(partCents).toFloat() / totalCents.toFloat()

/**
 * 占比的分母：各项金额的**绝对值之和**。
 *
 * 全为正数时与净额完全相同（对既有数据零变化），只有出现退款这类负项时才不同。
 */
internal fun absoluteTotalOf(amountsCents: Iterable<Long>): Long =
    amountsCents.sumOf { abs(it) }
