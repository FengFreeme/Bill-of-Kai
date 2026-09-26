package com.kai.bill.feature.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kai.bill.core.design.component.SegmentTabs
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 统计的时间粒度。
 *
 * 定义在 `feature` 而非 `domain`：它纯粹是「用户在统计页选了哪一档」，
 * 属于 UI 状态；真正的区间换算在 `core:common` 的 TimeRange 里按毫秒完成，
 * domain 层只认 [com.kai.bill.domain.model.DateRange]，不关心粒度名称。
 *
 * @property label 分段切换器上的显示文案
 */
enum class DateRangeKind(val label: String) {

    /** 按自然日 */
    DAY("日"),

    /** 按自然周（周一为周首日） */
    WEEK("周"),

    /** 按自然月 */
    MONTH("月"),

    /** 按自然年 */
    YEAR("年")
}

/**
 * 日 / 周 / 月 / 年 切换器，统计页与首页共用。
 *
 * 直接复用设计系统的 [SegmentTabs]：切换器的视觉与交互不该有两套实现，
 * 这里只负责把枚举映射成文案。
 *
 * @param selected 当前选中的粒度
 * @param onSelect 切换回调
 */
@Composable
fun DateRangeSwitcher(
    selected: DateRangeKind,
    onSelect: (DateRangeKind) -> Unit,
    modifier: Modifier = Modifier
) {
    SegmentTabs(
        items = DateRangeKind.entries,
        selected = selected,
        onSelect = onSelect,
        labelOf = { it.label },
        modifier = modifier
    )
}

/**
 * 把当前粒度的锚定日期翻译成标题，显示在粒度切换器下方的「可点选日期」。
 *
 * - [DateRangeKind.DAY] → "9月13日"
 * - [DateRangeKind.WEEK] → "9/7-9/13 第2周"（周数每月重置）
 * - [DateRangeKind.MONTH] → "2026年9月"
 * - [DateRangeKind.YEAR] → "2026年"
 *
 * @param kind 当前粒度
 * @param millis 锚定毫秒
 * @param zone 时区，默认系统时区
 */
fun rangeLabel(kind: DateRangeKind, millis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    return when (kind) {
        DateRangeKind.DAY -> "${date.monthValue}月${date.dayOfMonth}日"
        DateRangeKind.WEEK -> {
            val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
            val sunday = monday.plusDays(6)
            val w = (sunday.dayOfMonth + 6) / 7
            "${monday.monthValue}/${monday.dayOfMonth}-${sunday.monthValue}/${sunday.dayOfMonth} 第${w}周"
        }
        DateRangeKind.MONTH -> "${date.year}年${date.monthValue}月"
        DateRangeKind.YEAR -> "${date.year}年"
    }
}

/**
 * 周标签，如 "8/17-8/23 第4周"；周数按自然月重置（取该周周日的日序算第几周）。
 */
fun weekLabel(startMonday: LocalDate): String {
    val end = startMonday.plusDays(6)
    val w = (end.dayOfMonth + 6) / 7
    return "${startMonday.monthValue}/${startMonday.dayOfMonth}-${end.monthValue}/${end.dayOfMonth} 第${w}周"
}
