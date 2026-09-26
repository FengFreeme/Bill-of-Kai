package com.kai.bill.domain.time

import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 「跨日」信号源。
 *
 * **为什么需要它**：概览与预算的「今日 / 本月」区间，是在**构造流时**由 [MonthDayRanges]
 * 算一次就作为实参传给聚合查询的（`observeOverview(ranges.today())`），订阅之后不会因为
 * 时间流逝而重算。真机上的表现：首页流水列表已经翻到新的一天（列表每次发射都用
 * `LocalDate.now()` 重算标签），预算卡的「今日已花」却还停在前一天的数字 ——
 * 同一屏上两个「今日」对不上。
 *
 * **用法**：把依赖区间的流挂在本信号下游，每次发射时重新推导区间：
 * ```
 * dayTicker.ticks().flatMapLatest {
 *     combine(observeOverview(ranges.today()), …) { … }
 * }
 * ```
 *
 * 订阅时**立即**发射一次（首帧就有数据），此后每天本地 00:00 各发射一次；
 * 配合 `SharingStarted.WhileSubscribed`，从后台切回前台重新订阅时也会立即重算。
 */
class DayTicker @Inject constructor(
    private val clock: Clock,
    private val ranges: MonthDayRanges
) {

    /** 现在发射一次，之后每天本地 00:00 发射一次 */
    fun ticks(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(millisUntilNextDay())
        }
    }

    /**
     * 距离下一个本地 00:00 的毫秒数，至少 1ms。
     *
     * 用 [MonthDayRanges.today] 的「今天最后一刻」加 1ms 反推，而不是在这里再写一遍
     * 本地日界 —— 时区口径只能有一份，否则夏令时 / 时区变更时两处会不一致。
     */
    internal fun millisUntilNextDay(): Long =
        (ranges.today().endMillis + 1L - clock.nowMillis()).coerceAtLeast(1L)
}
