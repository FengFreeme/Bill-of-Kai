package com.kai.bill.feature.stats.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.domain.model.stats.TrendPoint
import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow

/**
 * 趋势折线图（自绘）。
 *
 * 把 [TrendPoint] 折成一条带面积填充的折线：年报按月、其余按日。
 * 与 [DonutChart] 同源，文字仍用 [rememberTextMeasurer] 量好再画，
 * 避免中文宽度估算偏差；金额统一用「元」（分 ÷ 100）入图。
 *
 * @param points 趋势点（按时间升序）
 * @param monthly true 表示每个点是「一个月」—— 标签显示「9月」，否则「9/12」
 * @param modifier 外部修饰符
 */
@Composable
fun TrendLineChart(
    points: List<TrendPoint>,
    monthly: Boolean,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    val primary = AppTheme.color.primary
    val labelColor = AppTheme.color.onSurfaceVariant
    val gridColor = AppTheme.color.outline
    val axisColor = AppTheme.color.outline

    Canvas(modifier = modifier.fillMaxSize()) {
        if (points.isEmpty()) return@Canvas

        val maxVal = max(points.maxOf { it.amountCents / 100.0 }, 1.0).toFloat()
        val niceMax = niceAxisMax(maxVal)
        val yTicks = listOf(0f, niceMax * 0.25f, niceMax * 0.5f, niceMax * 0.75f, niceMax)

        // 先量 Y 轴刻度文字，动态留左内边距
        val yLayouts = yTicks.map { tick ->
            measurer.measure(
                text = formatAxisAmount(tick),
                style = TextStyle(color = labelColor, fontSize = 9.sp)
            )
        }
        val yLabelW = yLayouts.maxOf { it.size.width }.toFloat()
        val padL = yLabelW + 8.dp.toPx()
        val padR = 10.dp.toPx()
        val padT = 16.dp.toPx()
        val padB = 22.dp.toPx()
        val plotW = size.width - padL - padR
        val plotH = size.height - padT - padB
        val n = points.size

        fun xAt(i: Int): Float = when (n) {
            1 -> padL + plotW / 2f
            else -> padL + plotW * i / (n - 1)
        }
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
            // 刻度短线
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

        val linePath = Path().apply {
            points.forEachIndexed { i, p ->
                val x = xAt(i)
                val y = yAt(p.amountCents / 100.0)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        val areaPath = Path().apply {
            moveTo(xAt(0), padT + plotH)
            points.forEachIndexed { i, p -> lineTo(xAt(i), yAt(p.amountCents / 100.0)) }
            lineTo(xAt(n - 1), padT + plotH)
            close()
        }
        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(primary.copy(alpha = 0.30f), primary.copy(alpha = 0.02f))
            )
        )
        drawPath(
            path = linePath,
            color = primary,
            style = Stroke(width = 2.dp.toPx())
        )
        points.forEachIndexed { i, p ->
            drawCircle(
                color = primary,
                radius = 3.dp.toPx(),
                center = Offset(xAt(i), yAt(p.amountCents / 100.0))
            )
        }

        // x 轴标签：最多约 6 个，避免拥挤
        val step = (n / 6).coerceAtLeast(1)
        points.forEachIndexed { i, p ->
            if (i % step != 0 && i != n - 1) return@forEachIndexed
            val label = formatAxisLabel(p.startMillis, monthly)
            val layout = measurer.measure(
                text = label,
                style = TextStyle(color = labelColor, fontSize = 9.sp)
            )
            val x = xAt(i)
            val tx = (x - layout.size.width / 2f).coerceIn(padL, size.width - layout.size.width)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(tx, padT + plotH + 4.dp.toPx())
            )
        }
    }
}

private fun formatAxisLabel(startMillis: Long, monthly: Boolean): String {
    val date = Instant.ofEpochMilli(startMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return if (monthly) "${date.monthValue}月" else "${date.monthValue}/${date.dayOfMonth}"
}

/** 把最大值抬到好看的刻度上（1 / 2 / 5 × 10^n） */
internal fun niceAxisMax(rawMax: Float): Float {
    if (rawMax <= 0f) return 1f
    val exp = floorLog10(rawMax.toDouble())
    val base = 10.0.pow(exp).toFloat()
    val norm = rawMax / base
    val niceNorm = when {
        norm <= 1f -> 1f
        norm <= 2f -> 2f
        norm <= 5f -> 5f
        else -> 10f
    }
    return niceNorm * base
}

private fun floorLog10(v: Double): Int {
    var e = 0
    var x = v
    while (x >= 10.0) {
        x /= 10.0
        e++
    }
    while (x < 1.0 && e > -8) {
        x *= 10.0
        e--
    }
    return e
}

/** Y 轴金额刻度：大数用 k，小数保留整数/一位 */
internal fun formatAxisAmount(value: Float): String {
    val v = value.toDouble()
    return when {
        v >= 10000 -> String.format("%.0fk", v / 1000.0)
        v >= 1000 -> String.format("%.1fk", v / 1000.0).trimEnd('0').trimEnd('.')
        v >= 100 -> String.format("%.0f", v)
        v == ceil(v) -> String.format("%.0f", v)
        else -> String.format("%.1f", v)
    }
}
