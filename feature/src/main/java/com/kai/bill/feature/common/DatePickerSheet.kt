package com.kai.bill.feature.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kai.bill.core.design.theme.AppTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 日 / 周 / 月 / 年 的取值弹层（底部抽屉）。
 *
 * 复刻筛选面板的「遮罩 + 底部面板」结构：点遮罩关闭，面板自身吃掉点击。
 * 内部按 [kind] 切换四种取值视图，选中后回调锚定毫秒并关闭。
 *
 * @param kind 当前粒度，决定弹层里展示日历 / 周列表 / 月网格 / 年网格
 * @param selectedDate 当前已选锚定毫秒，用于高亮
 * @param nowMillis 当前时间，用于标记「今天 / 本周」
 * @param onSelect 选中某个具体日期后回调（毫秒）
 * @param onDismiss 关闭弹层
 */
@Composable
fun DatePickerSheet(
    kind: DateRangeKind,
    selectedDate: Long,
    nowMillis: Long = System.currentTimeMillis(),
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val zone = ZoneId.systemDefault()
    val selected = Instant.ofEpochMilli(selectedDate).atZone(zone).toLocalDate()
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) { },
            color = AppTheme.color.surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 20.dp)
            ) {
                // 标题栏：粒度名 + 关闭
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "选择${kind.label}",
                        style = AppTheme.typography.titleMedium,
                        color = AppTheme.color.onSurface
                    )
                    Text(
                        text = "完成",
                        style = AppTheme.typography.labelLarge,
                        color = AppTheme.color.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                when (kind) {
                    DateRangeKind.DAY -> DayBody(selected, now, zone, onSelect)
                    DateRangeKind.WEEK -> WeekBody(selected, now, zone, onSelect)
                    DateRangeKind.MONTH -> MonthBody(selected, onSelect)
                    DateRangeKind.YEAR -> YearBody(selected, onSelect)
                }
            }
        }
    }
}

/** 把 [LocalDate] 转成锚定毫秒：日 / 月 / 年取当天 0 点，周取周一 0 点 */
private fun LocalDate.toAnchorMillis(zone: ZoneId): Long =
    this.atStartOfDay(zone).toInstant().toEpochMilli()

@Composable
private fun DayBody(
    selected: LocalDate,
    now: LocalDate,
    zone: ZoneId,
    onSelect: (Long) -> Unit
) {
    val view = remember { androidx.compose.runtime.mutableStateOf(YearMonth.from(selected)) }
    val firstOfMonth = view.value.atDay(1)
    val leading = (firstOfMonth.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val days = view.value.lengthOfMonth()

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        MonthHeader(
            title = "${view.value.year}年${view.value.monthValue}月",
            onPrev = { view.value = view.value.minusMonths(1) },
            onNext = { view.value = view.value.plusMonths(1) }
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Text(
                    text = it,
                    style = AppTheme.typography.labelSmall,
                    color = AppTheme.color.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }
        val cells = buildList {
            repeat(leading) { add(null) }
            for (d in 1..days) add(firstOfMonth.withDayOfMonth(d))
        }
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            val isSel = date == selected
                            val isToday = date == now
                            PickerCell(
                                text = date.dayOfMonth.toString(),
                                selected = isSel,
                                withDot = isToday && !isSel,
                                onClick = { onSelect(date.toAnchorMillis(zone)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekBody(
    selected: LocalDate,
    now: LocalDate,
    zone: ZoneId,
    onSelect: (Long) -> Unit
) {
    val view = remember { androidx.compose.runtime.mutableStateOf(YearMonth.from(selected)) }
    val firstOfMonth = view.value.atDay(1)
    val firstMonday = firstOfMonth.minusDays(
        (firstOfMonth.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7L
    )
    val lastOfMonth = view.value.atEndOfMonth()
    val weeks = buildList {
        var cur = firstMonday
        while (cur <= lastOfMonth) {
            add(cur)
            cur = cur.plusWeeks(1)
        }
    }
    val selectedMonday = selected.minusDays((selected.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7L)

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        MonthHeader(
            title = "${view.value.year}年${view.value.monthValue}月",
            onPrev = { view.value = view.value.minusMonths(1) },
            onNext = { view.value = view.value.plusMonths(1) }
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            weeks.forEach { monday ->
                val isSel = monday == selectedMonday
                val isThisWeek = now >= monday && now <= monday.plusDays(6)
                val label = weekLabel(monday)
                val (range, week) = label.split(" ").let { it[0] to it.getOrNull(1).orEmpty() }
                val caption = buildString {
                    append(range)
                    if (isThisWeek) append(" · 本周")
                    if (week.isNotEmpty()) append(" $week")
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(monday.toAnchorMillis(zone)) },
                    color = if (isSel) AppTheme.color.primary else AppTheme.color.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = caption,
                        style = AppTheme.typography.bodyMedium,
                        color = if (isSel) AppTheme.color.onPrimary else AppTheme.color.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthBody(
    selected: LocalDate,
    onSelect: (Long) -> Unit
) {
    val viewYear = remember { androidx.compose.runtime.mutableIntStateOf(selected.year) }
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        MonthHeader(
            title = "${viewYear.value}年",
            onPrev = { viewYear.value -= 1 },
            onNext = { viewYear.value += 1 }
        )
        val months = (1..12).map { it }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.height(220.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(months.size) { idx ->
                val m = months[idx]
                val isSel = m == selected.monthValue && viewYear.value == selected.year
                PickerCell(
                    text = "${m}月",
                    selected = isSel,
                    onClick = { onSelect(LocalDate.of(viewYear.value, m, 1).toAnchorMillis(ZoneId.systemDefault())) }
                )
            }
        }
    }
}

@Composable
private fun YearBody(
    selected: LocalDate,
    onSelect: (Long) -> Unit
) {
    val base = remember { androidx.compose.runtime.mutableIntStateOf((selected.year / 12) * 12) }
    val years = (base.value until base.value + 12).toList()
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        MonthHeader(
            title = "${base.value} – ${base.value + 11}",
            onPrev = { base.value -= 12 },
            onNext = { base.value += 12 }
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.height(220.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(years.size) { idx ->
                val y = years[idx]
                val isSel = y == selected.year
                PickerCell(
                    text = "${y}年",
                    selected = isSel,
                    onClick = { onSelect(LocalDate.of(y, 1, 1).toAnchorMillis(ZoneId.systemDefault())) }
                )
            }
        }
    }
}

@Composable
private fun MonthHeader(
    title: String,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "‹",
            style = AppTheme.typography.titleLarge,
            color = AppTheme.color.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onPrev)
                .padding(horizontal = 12.dp, vertical = 2.dp)
        )
        Text(
            text = title,
            style = AppTheme.typography.titleMedium,
            color = AppTheme.color.onSurface
        )
        Text(
            text = "›",
            style = AppTheme.typography.titleLarge,
            color = AppTheme.color.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onNext)
                .padding(horizontal = 12.dp, vertical = 2.dp)
        )
    }
}

/**
 * 统一的可点选单元格：选中时满色底 + 主色文字，未选中时浅底。
 *
 * @param withDot 未选中但需标记（如「今天」）时，在文字下方画一个小圆点
 */
@Composable
private fun PickerCell(
    text: String,
    selected: Boolean,
    withDot: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AppTheme.color.primary else AppTheme.color.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text,
                style = AppTheme.typography.bodyMedium,
                color = if (selected) AppTheme.color.onPrimary else AppTheme.color.onSurface,
                textAlign = TextAlign.Center
            )
            if (withDot) {
                Box(
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .size(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(AppTheme.color.primary)
                )
            }
        }
    }
}
