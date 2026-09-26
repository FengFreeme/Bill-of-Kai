package com.kai.bill.core.common.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 时间区间计算工具。
 *
 * 返回 [LongRange]（`startMillis..endMillis`，闭区间），可直接用于 Room 的
 * `WHERE time BETWEEN :start AND :end`；换算全程在指定时区内完成，不会跨时区/夏令时少一天。
 * minSdk 31，java.time 系统内置，无需 desugaring。
 */
object TimeRange {

    /** 返回 [epochMillis] 所在自然日 00:00:00.000 ~ 23:59:59.999 的闭区间 */
    fun day(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LongRange {
        val date = localDateOf(epochMillis, zone)
        return startOfDayMillis(date, zone)..endOfDayMillis(date, zone)
    }

    /** 返回 [epochMillis] 所在自然周（周一起）周一 ~ 周日的闭区间 */
    fun week(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LongRange {
        val date = localDateOf(epochMillis, zone)
        val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
        val sunday = monday.plusDays(6)
        return startOfDayMillis(monday, zone)..endOfDayMillis(sunday, zone)
    }

    /** 返回 [epochMillis] 所在自然月 1 日 ~ 月末的闭区间 */
    fun month(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LongRange {
        val firstDay = localDateOf(epochMillis, zone).withDayOfMonth(1)
        return startOfDayMillis(firstDay, zone)..endOfDayMillis(endOfMonth(firstDay), zone)
    }

    /** 返回 [epochMillis] 所在自然年 1/1 ~ 12/31 的闭区间 */
    fun year(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LongRange {
        val firstDay = localDateOf(epochMillis, zone).withDayOfYear(1)
        return startOfDayMillis(firstDay, zone)..endOfDayMillis(firstDay.plusYears(1).minusDays(1), zone)
    }

    /**
     * 「发薪日起算」等非自然月场景：今天 5 号、起始日 10 号时，当前周期是「上月 10 日 ~ 本月 9 日」。
     *
     * @param startDay 周期起始日 1~31；当月不足该天数（如 2 月 31 日）时退化为当月最后一天
     */
    fun monthWithStartDay(
        epochMillis: Long,
        startDay: Int,
        zone: ZoneId = ZoneId.systemDefault()
    ): LongRange {
        val today = localDateOf(epochMillis, zone)
        val anchor = if (today.dayOfMonth >= startDay) {
            today.withDayOfMonth(startDay.coerceAtMost(today.lengthOfMonth()))
        } else {
            // 还没到本月起始日 → 当前周期从「上个月起始日」开始
            val lastMonth = today.minusMonths(1)
            lastMonth.withDayOfMonth(startDay.coerceAtMost(lastMonth.lengthOfMonth()))
        }
        return startOfDayMillis(anchor, zone)..endOfDayMillis(anchor.plusMonths(1).minusDays(1), zone)
    }

    /**
     * 本月剩余天数（含今天），最小值 1 —— 保证弹性日预算
     * `今日可用 = 本月剩余额度 ÷ 本月剩余天数` 的除数不为零。
     */
    fun daysRemainingInMonth(
        epochMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Int {
        val today = localDateOf(epochMillis, zone)
        return (today.lengthOfMonth() - today.dayOfMonth + 1).coerceAtLeast(1)
    }

    /** 返回 [epochMillis] 所在月天数（28~31） */
    fun daysInMonth(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Int =
        localDateOf(epochMillis, zone).lengthOfMonth()

    /** 毫秒时间戳 → 指定时区下的本地日期 */
    private fun localDateOf(epochMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    /** 某日 00:00:00.000 的毫秒时间戳 */
    private fun startOfDayMillis(date: LocalDate, zone: ZoneId): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** 某日 23:59:59.999 的毫秒时间戳 */
    private fun endOfDayMillis(date: LocalDate, zone: ZoneId): Long =
        date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    /** 当月最后一天 */
    private fun endOfMonth(firstDay: LocalDate): LocalDate =
        firstDay.plusMonths(1).minusDays(1)
}
