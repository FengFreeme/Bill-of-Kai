package com.kai.bill.feature.stats.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kai.bill.core.common.money.MoneyFormatter
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.domain.model.stats.TrendPoint
import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 趋势折线图（自绘）。
 *
 * 把 [TrendPoint] 折成一条带面积填充的折线：年报按月、其余按日。
 * 文字用 [rememberTextMeasurer] 量好再画，避免中文宽度估算偏差；金额统一用「元」入图。
 *
 * 点按顶点即可选中：放大高亮 + 虚线参考线 + 金额气泡；再点同一点或空白处取消选中。
 *
 * @param points 趋势点（按时间升序）
 * @param monthly true 表示每个点是「一个月」—— 标签显示「9月」，否则「9/12」
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
    val bubbleColor = AppTheme.color.surface
    val bubbleBorder = AppTheme.color.outline
    val bubbleTextColor = AppTheme.color.onSurface

    val axisStyle = remember(labelColor) { TextStyle(color = labelColor, fontSize = 9.sp) }
    val bubbleStyle = remember(bubbleTextColor) {
        TextStyle(color = bubbleTextColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }

    // points 变化（切换时间范围）时自动清空选中，避免选中错位的点
    var selectedIndex by remember(points) { mutableIntStateOf(-1) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(points) {
                val density = this
                val tolerance = with(density) { 28.dp.toPx() }
                detectTapGestures { offset ->
                    if (points.isEmpty()) return@detectTapGestures
                    val geo = trendGeometry(
                        size = Size(size.width.toFloat(), size.height.toFloat()),
                        points = points,
                        measurer = measurer,
                        density = density,
                        axisStyle = axisStyle
                    )
                    // 取离触点最近的顶点
                    var nearest = -1
                    var nearestDist = Float.MAX_VALUE
                    points.indices.forEach { i ->
                        val d = hypot(
                            offset.x - geo.xAt(i),
                            offset.y - geo.yAt(points[i].amountCents)
                        )
                        if (d < nearestDist) {
                            nearestDist = d
                            nearest = i
                        }
                    }
                    // 命中别的点 → 选中它；点同一个点 / 空白 / 超出容差 → 取消
                    selectedIndex =
                        if (nearest >= 0 && nearestDist <= tolerance && nearest != selectedIndex) {
                            nearest
                        } else {
                            -1
                        }
                }
            }
    ) {
        if (points.isEmpty()) return@Canvas

        val geo = trendGeometry(
            size = size,
            points = points,
            measurer = measurer,
            density = this,
            axisStyle = axisStyle
        )
        val n = points.size

        with(geo) {
            // Y 轴主线
            drawLine(
                color = axisColor.copy(alpha = 0.55f),
                start = Offset(padL, padT),
                end = Offset(padL, padT + plotH),
                strokeWidth = 1.dp.toPx()
            )
            // Y 轴刻度 + 网格
            yTicks.forEachIndexed { idx, tick ->
                val y = yOf(tick.toDouble())
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
                    val y = yAt(p.amountCents)
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            val areaPath = Path().apply {
                moveTo(xAt(0), padT + plotH)
                points.forEachIndexed { i, p -> lineTo(xAt(i), yAt(p.amountCents)) }
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
                    center = Offset(xAt(i), yAt(p.amountCents))
                )
            }

            // x 轴标签：最多 6 个，按索引**均分**且首尾必取（原因见 axisTickIndices）
            axisTickIndices(count = n, maxTicks = AXIS_MAX_TICKS).forEach { i ->
                val label = formatAxisLabel(points[i].startMillis, monthly)
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

            // 选中的顶点：参考虚线 + 放大高亮 + 金额气泡（最后画，保证压在最上层）
            if (selectedIndex in points.indices) {
                val point = points[selectedIndex]
                val px = xAt(selectedIndex)
                val py = yAt(point.amountCents)

                // 竖直参考虚线：从顶点落到 X 轴
                drawLine(
                    color = primary.copy(alpha = 0.45f),
                    start = Offset(px, py),
                    end = Offset(px, padT + plotH),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(4.dp.toPx(), 4.dp.toPx())
                    )
                )
                // 顶点高亮：光晕 + 挖白 + 实心点
                drawCircle(
                    color = primary.copy(alpha = 0.22f),
                    radius = 8.dp.toPx(),
                    center = Offset(px, py)
                )
                drawCircle(
                    color = bubbleColor,
                    radius = 5.dp.toPx(),
                    center = Offset(px, py)
                )
                drawCircle(
                    color = primary,
                    radius = 3.5.dp.toPx(),
                    center = Offset(px, py)
                )

                // 金额气泡：默认在顶点上方，顶不下时翻到下方
                val bubbleLayout = measurer.measure(
                    text = "¥${MoneyFormatter.plain(point.amountCents)}",
                    style = bubbleStyle
                )
                val padH = 8.dp.toPx()
                val padV = 5.dp.toPx()
                val bubbleW = bubbleLayout.size.width + padH * 2
                val bubbleH = bubbleLayout.size.height + padV * 2
                val bubbleX = (px - bubbleW / 2f).coerceIn(0f, size.width - bubbleW)
                val above = py - bubbleH - 10.dp.toPx()
                val bubbleY = if (above >= 0f) above else py + 10.dp.toPx()

                drawRoundRect(
                    color = bubbleColor,
                    topLeft = Offset(bubbleX, bubbleY),
                    size = Size(bubbleW, bubbleH),
                    cornerRadius = CornerRadius(8.dp.toPx())
                )
                drawRoundRect(
                    color = bubbleBorder.copy(alpha = 0.6f),
                    topLeft = Offset(bubbleX, bubbleY),
                    size = Size(bubbleW, bubbleH),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
                drawText(
                    textLayoutResult = bubbleLayout,
                    topLeft = Offset(bubbleX + padH, bubbleY + padV)
                )
            }
        }
    }
}

/**
 * 折线图内部布局。
 *
 * 抽出来是为了让 [Canvas] 绘制与 `pointerInput` 点击命中共用同一套坐标 ——
 * 两边各算一份的话，只要一边的参数（如 Y 轴文字宽度）变动，点选就会错位。
 */
private class TrendGeometry(
    private val points: List<TrendPoint>,
    size: Size,
    val padL: Float,
    val padR: Float,
    val padT: Float,
    val padB: Float,
    val niceMax: Float,
    val yTicks: List<Float>,
    val yLayouts: List<TextLayoutResult>
) {
    val plotW: Float = size.width - padL - padR
    val plotH: Float = size.height - padT - padB

    /** 第 [i] 个点的横坐标 */
    fun xAt(i: Int): Float = when (points.size) {
        1 -> padL + plotW / 2f
        else -> padL + plotW * i / (points.size - 1)
    }

    /** 金额（分）对应的纵坐标 */
    fun yAt(cents: Long): Float = yOf(cents / 100.0)

    /** 金额（元）对应的纵坐标 */
    fun yOf(value: Double): Float = padT + plotH * (1f - (value / niceMax).toFloat())
}

/** 按当前画布尺寸算出布局；Y 轴文字宽度会动态决定左内边距 */
private fun trendGeometry(
    size: Size,
    points: List<TrendPoint>,
    measurer: TextMeasurer,
    density: Density,
    axisStyle: TextStyle
): TrendGeometry {
    val maxVal = max(points.maxOf { it.amountCents / 100.0 }, 1.0).toFloat()
    val niceMax = niceAxisMax(maxVal)
    val yTicks = listOf(0f, niceMax * 0.25f, niceMax * 0.5f, niceMax * 0.75f, niceMax)
    val yLayouts = yTicks.map { tick ->
        measurer.measure(text = formatAxisAmount(tick), style = axisStyle)
    }
    val yLabelW = yLayouts.maxOf { it.size.width }.toFloat()
    return TrendGeometry(
        points = points,
        size = size,
        padL = with(density) { yLabelW + 8.dp.toPx() },
        padR = with(density) { 10.dp.toPx() },
        padT = with(density) { 16.dp.toPx() },
        padB = with(density) { 22.dp.toPx() },
        niceMax = niceMax,
        yTicks = yTicks,
        yLayouts = yLayouts
    )
}

/** x 轴最多画几个刻度（含首尾两个） */
private const val AXIS_MAX_TICKS = 6

/**
 * x 轴刻度取哪些数据点的下标：按索引均分，且首尾必取。
 *
 * 不能写成「每 step 个取一个 + 末尾补一个」—— 末尾那个常紧贴前一个刻度
 * （26 个点、step=4 时取到 24 与 25，只差约 4% 图宽），两个日期标签会叠字。
 *
 * @param maxTicks 最多几个刻度（含首尾）；点数不足时全部画出
 */
internal fun axisTickIndices(count: Int, maxTicks: Int = AXIS_MAX_TICKS): List<Int> {
    if (count <= 0) return emptyList()
    if (count <= maxTicks) return (0 until count).toList()
    val last = count - 1
    val span = maxTicks - 1
    return (0 until maxTicks)
        .map { k -> (k.toFloat() * last / span).roundToInt() }
        .distinct()
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
