package com.kai.bill.feature.stats.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kai.bill.core.common.money.MoneyFormatter
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.feature.stats.WeeklyBar
import kotlin.math.max

/**
 * 周支出对比柱状图（自绘）。
 *
 * 直接复用 [TrendLineChart] 同款的画笔与文字量法：每根柱子对应一个自然周，
 * 最后一柱是「锚定周」（当前选中或最近一周），用满色高亮，其余半透明，
 * 一眼能看出这周相对上几周是多了还是少了。
 *
 * @param bars 周桶，按时间升序（末位为锚定周）
 * @param modifier 外部修饰符
 */
@Composable
fun WeeklyBarChart(
    bars: List<WeeklyBar>,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    val primary = AppTheme.color.primary
    val labelColor = AppTheme.color.onSurfaceVariant
    val gridColor = AppTheme.color.outline
    val axisColor = AppTheme.color.outline

    Canvas(modifier = modifier.fillMaxSize()) {
        if (bars.isEmpty()) return@Canvas

        val maxVal = max(bars.maxOf { it.amountCents / 100.0 }, 1.0).toFloat()
        val niceMax = niceAxisMax(maxVal)
        val yTicks = listOf(0f, niceMax * 0.25f, niceMax * 0.5f, niceMax * 0.75f, niceMax)

        val yLayouts = yTicks.map { tick ->
            measurer.measure(
                text = formatAxisAmount(tick),
                style = TextStyle(color = labelColor, fontSize = 9.sp)
            )
        }
        val yLabelW = yLayouts.maxOf { it.size.width }.toFloat()
        val padL = yLabelW + 8.dp.toPx()
        val padR = 8.dp.toPx()
        val padT = 18.dp.toPx()
        val padB = 30.dp.toPx()
        val plotW = size.width - padL - padR
        val plotH = size.height - padT - padB
        val n = bars.size
        val gap = 10.dp.toPx()
        val barW = (plotW - gap * (n - 1)) / n
        val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

        fun yAt(v: Double): Float = padT + plotH * (1f - (v / niceMax).toFloat())

        // Y 轴主线
        drawLine(
            color = axisColor.copy(alpha = 0.55f),
            start = Offset(padL, padT),
            end = Offset(padL, padT + plotH),
            strokeWidth = 1.dp.toPx()
        )
        // Y 轴刻度 + 网格
        yTicks.forEachIndexed { idx, tick ->
            val y = yAt(tick.toDouble())
            drawLine(
                color = gridColor.copy(alpha = if (idx == 0) 0.40f else 0.22f),
                start = Offset(padL, y),
                end = Offset(size.width - padR, y),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = axisColor.copy(alpha = 0.55f),
                start = Offset(padL - 3.dp.toPx(), y),
                end = Offset(padL, y),
                strokeWidth = 1.dp.toPx()
            )
            val layout = yLayouts[idx]
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    padL - 5.dp.toPx() - layout.size.width,
                    (y - layout.size.height / 2f).coerceIn(0f, size.height - layout.size.height)
                )
            )
        }

        // X 轴主线
        drawLine(
            color = axisColor.copy(alpha = 0.55f),
            start = Offset(padL, padT + plotH),
            end = Offset(size.width - padR, padT + plotH),
            strokeWidth = 1.dp.toPx()
        )

        bars.forEachIndexed { i, bar ->
            val x = padL + i * (barW + gap)
            val barH = ((bar.amountCents / 100.0 / niceMax).toFloat() * plotH).coerceAtLeast(0f)
            val y = padT + plotH - barH
            val isLast = i == n - 1
            drawRoundRect(
                color = if (isLast) primary else primary.copy(alpha = 0.5f),
                topLeft = Offset(x, y),
                size = Size(barW, barH.coerceAtLeast(2.dp.toPx())),
                cornerRadius = radius
            )

            // 金额（元）画在柱子顶部
            if (bar.amountCents > 0) {
                val amountText = MoneyFormatter.plain(bar.amountCents)
                val amountLayout = measurer.measure(
                    text = amountText,
                    style = TextStyle(color = labelColor, fontSize = 9.sp)
                )
                val ax = (x + barW / 2f - amountLayout.size.width / 2f)
                    .coerceIn(padL, size.width - amountLayout.size.width)
                drawText(
                    textLayoutResult = amountLayout,
                    topLeft = Offset(ax, (y - amountLayout.size.height - 3.dp.toPx()).coerceAtLeast(2.dp.toPx()))
                )
            }

            // 日期范围画在柱底（两行：区间 + 第几周）
            val parts = bar.label.split(" ")
            val rangeLayout = measurer.measure(
                text = parts.firstOrNull().orEmpty(),
                style = TextStyle(color = labelColor, fontSize = 9.sp)
            )
            val weekLayout = measurer.measure(
                text = parts.getOrNull(1).orEmpty(),
                style = TextStyle(color = labelColor, fontSize = 9.sp)
            )
            val cx = (x + barW / 2f).coerceIn(0f, size.width)
            val rangeX = (cx - rangeLayout.size.width / 2f)
                .coerceIn(0f, size.width - rangeLayout.size.width)
            drawText(
                textLayoutResult = rangeLayout,
                topLeft = Offset(rangeX, padT + plotH + 4.dp.toPx())
            )
            val weekX = (cx - weekLayout.size.width / 2f)
                .coerceIn(0f, size.width - weekLayout.size.width)
            drawText(
                textLayoutResult = weekLayout,
                topLeft = Offset(weekX, padT + plotH + 4.dp.toPx() + rangeLayout.size.height)
            )
        }
    }
}
