package com.kai.bill.core.common.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 时间区间计算工具：生成日 / 月 / 年对应的毫秒区间。
 *
 * 返回值为 [LongRange]（`startMillis..endMillis`，**闭区间**），
 * 可直接用于 Room 的 `WHERE time BETWEEN :start AND :end` 查询。
 *
 * 所有换算都在指定时区内完成，跨时区或跨夏令时不会出现"少一天/多一天"。
 * 本项目 minSdk 为 31，java.time 已是系统内置，无需 desugaring。
 */
object TimeRange {

    /**
     * 计算某个时刻所在「自然日」的区间。
     *
     * @param epochMillis 该日内的任意时刻（毫秒）
     * @param zone 时区，默认系统时区
     * @return 当日 00:00:00.000 至 23:59:59.999 的闭区间
     */
    fun day(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LongRange {
        val date = localDateOf(epochMillis, zone)
        return startOfDayMillis(date, zone)..endOfDayMillis(date, zone)
    }

    /**
     * 计算某个时刻所在「自然周」的区间（周一为周首日）。
     *
     * @param epochMillis 该周内的任意时刻（毫秒）
     * @param zone 时区，默认系统时区
     * @return 当周周一 00:00:00.000 至周日 23:59:59.999 的闭区间
     */
    fun week(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LongRange {
        val date = localDateOf(epochMillis, zone)
        val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
        val sunday = monday.plusDays(6)
        return startOfDayMillis(monday, zone)..endOfDayMillis(sunday, zone)
    }

    /**
     * 计算某个时刻所在「自然月」的区间。
     *
     * @param epochMillis 该月内的任意时刻（毫秒）
     * @param zone 时区，默认系统时区
     * @return 当月 1 日 00:00:00.000 至月末 23:59:59.999 的闭区间
     */
    fun month(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LongRange {
        val firstDay = localDateOf(epochMillis, zone).withDayOfMonth(1)
        return startOfDayMillis(firstDay, zone)..endOfDayMillis(endOfMonth(firstDay), zone)
    }

    /**
     * 计算某个时刻所在「自然年」的区间。
     *
     * @param epochMillis 该年内的任意时刻（毫秒）
     * @param zone 时区，默认系统时区
     * @return 当年 1 月 1 日 00:00:00.000 至 12 月 31 日 23:59:59.999 的闭区间
     */
    fun year(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LongRange {
        val firstDay = localDateOf(epochMillis, zone).withDayOfYear(1)
        return startOfDayMillis(firstDay, zone)..endOfDayMillis(firstDay.plusYears(1).minusDays(1), zone)
    }

    /**
     * 计算「自定义起始日」的预算周期区间。
     *
     * 用于「发薪日起算」这类非自然月场景：若今天是 5 号且起始日设为 10 号，
     * 则当前周期是「上月 10 日 ~ 本月 9 日」。
     *
     * @param epochMillis 参考时刻（毫秒）
     * @param startDay 周期起始日，取值 1~31；若当月不足该天数（如 2 月 31 日），
     *                 自动退化为当月最后一天
     * @param zone 时区，默认系统时区
     * @return 一个完整预算周期的闭区间
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
     * 计算当月剩余天数（含今天）。
     *
     * 用于弹性日预算：`今日可用 = 本月剩余额度 ÷ 本月剩余天数`。
     *
     * @param epochMillis 参考时刻（毫秒）
     * @param zone 时区，默认系统时区
     * @return 剩余天数，最小值为 1（月末最后一天返回 1，保证除法不为零）
     */
    fun daysRemainingInMonth(
        epochMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Int {
        val today = localDateOf(epochMillis, zone)
        return (today.lengthOfMonth() - today.dayOfMonth + 1).coerceAtLeast(1)
    }

    /**
     * 获取某个月的总天数。
     *
     * @param epochMillis 该月内的任意时刻（毫秒）
     * @param zone 时区，默认系统时区
     * @return 当月天数，取值 28~31
     */
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
