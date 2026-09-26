package com.kai.bill.domain.time

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * 跨日信号源的时点计算。
 *
 * 用 [FixedClock] + [ZoneId.systemDefault]（与 [MonthDayRanges] 同一套口径）钉死，
 * 断言的是「距离下一个本地 00:00 还有多久」——它是「今日已花会不会停在昨天」的关键。
 */
class DayTickerTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    @Test
    fun `下午四点-距离次日零点还有八小时`() {
        val now = at(2026, 9, 26, 16, 0)

        assertThat(tickerAt(now).millisUntilNextDay())
            .isEqualTo(Duration.ofHours(8).toMillis())
    }

    @Test
    fun `临近零点-剩余毫秒数如实反映`() {
        // 23:59:59.500 → 距次日 00:00 只剩 500ms
        val now = at(2026, 9, 26, 23, 59, 59) + 500L

        assertThat(tickerAt(now).millisUntilNextDay()).isEqualTo(500L)
    }

    @Test
    fun `正好卡在零点-下一次是明天零点也就是二十四小时`() {
        // 边界必须是 24h 而不是 0：算出 0 会让定时器空转（delay(0) 立刻回到循环）
        val now = LocalDate.of(2026, 9, 27).atStartOfDay(zone).toInstant().toEpochMilli()

        assertThat(tickerAt(now).millisUntilNextDay())
            .isEqualTo(Duration.ofDays(1).toMillis())
    }

    @Test
    fun `订阅时立即发射一次-首帧就有数据`() = runBlocking {
        // 只取第一个值：ticker 先 emit 再 delay，因此这里不会真的等 8 小时
        val emitted = tickerAt(at(2026, 9, 26, 16, 0)).ticks().first()

        assertThat(emitted).isNotNull()
    }

    private fun tickerAt(millis: Long): DayTicker {
        val clock = FixedClock(millis)
        return DayTicker(clock, MonthDayRanges(clock))
    }

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int = 0
    ): Long = LocalDateTime.of(year, month, day, hour, minute, second)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
}
