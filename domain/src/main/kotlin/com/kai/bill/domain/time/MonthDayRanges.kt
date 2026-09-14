package com.kai.bill.domain.time

import com.kai.bill.domain.model.DateRange
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * 当前「月 / 日」时间区间与剩余天数生成器（domain 自建，零 Android 依赖）。
 *
 * 预算进度需要「本月区间」「今日区间」「本月剩余天数」三个量，全部由 [Clock]
 * 给出的当前时刻 + 用户时区推导，禁止在 UseCase 里硬编码。
 *
 * 时区用 [ZoneId.systemDefault]：记账天然是「用户本地时间」的事，
 * 用 UTC 会让「今日 00:00」错位到用户认知之外。
 */
class MonthDayRanges @Inject constructor(
    private val clock: Clock
) {

    private val zone: ZoneId = ZoneId.systemDefault()

    /** 当前自然月区间：本月 1 日 00:00（含）~ 月末最后一刻（含） */
    fun currentMonth(): DateRange {
        val today = Instant.ofEpochMilli(clock.nowMillis()).atZone(zone).toLocalDate()
        val start = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.withDayOfMonth(today.lengthOfMonth())
            .atTime(23, 59, 59, 999_000_000)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        return DateRange(start, end)
    }

    /** 当前自然日区间：今天 00:00（含）~ 今天 23:59:59.999（含） */
    fun today(): DateRange {
        val today = Instant.ofEpochMilli(clock.nowMillis()).atZone(zone).toLocalDate()
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.atTime(23, 59, 59, 999_000_000)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        return DateRange(start, end)
    }

    /**
     * 本月剩余天数（含今天），最小为 1。
     * 例：1 月 31 天，15 号调用返回 17（15~31 共 17 天）。
     */
    fun daysRemainingInMonth(): Int {
        val today = Instant.ofEpochMilli(clock.nowMillis()).atZone(zone).toLocalDate()
        val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
        return ChronoUnit.DAYS.between(today, monthEnd).toInt() + 1
    }
}
