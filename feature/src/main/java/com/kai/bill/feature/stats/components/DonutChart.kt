package com.kai.bill.feature.stats.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kai.bill.core.common.percent.PercentFormatter
import com.kai.bill.core.design.theme.AppTheme
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 环形图的一个扇区。
 *
 * @property name 图例名（画在引线末端）
 * @property ratio 占比，0f..1f
 * @property color 扇区颜色
 */
data class DonutSlice(
    val name: String,
    val ratio: Float,
    val color: Color
)

/** 扇形从 12 点方向开始顺时针铺开 */
private const val START_ANGLE = -90f
private const val FULL_SWEEP = 360f

/** 高亮时引线的加粗倍数 */
private const val SELECTED_STROKE_FACTOR = 2f

/** 高亮扇区在**原地**加粗的倍数；配合 [SELECTED_STROKE_FACTOR] 形成「变粗」而非「弹出」 */
private const val SELECTED_WIDTH_FACTOR = 1.45f

/**
 * 默认显示标签的最小占比。
 *
 * 占比过小的扇区如果也画标签，几个小扇区的引线会在圆外挤成一团；
 * 这类小扇区改为**点击选中后**才显示标签与引线。
 */
private const val MIN_LABEL_RATIO = 0.05f

/**
 * 环形图布局尺寸（单位 px）。
 *
 * 抽出来是因为**命中检测**与**绘制**必须用完全一致的半径 / 圆心：
 * 以前两边各算一遍，改一处漏一处就会点不准。
 */
private class DonutMetrics(
    val center: Offset,
    val ringRadius: Float,
    val outerRadius: Float,
    val stubRadius: Float,
    val ringWidth: Float,
    val labelPad: Float,
    /** 命中检测的容差：留一点余量，高亮扇区加粗后也点得到 */
    val hitTolerance: Float,
    val canvasW: Float,
    val canvasH: Float
) {
    /** 点击是否落在圆环范围内（含容差） */
    fun isOnRing(offset: Offset): Boolean {
        val dx = offset.x - center.x
        val dy = offset.y - center.y
        val distance = sqrt(dx * dx + dy * dy)
        val inner = ringRadius - ringWidth / 2f - hitTolerance
        val outer = ringRadius + ringWidth / 2f + hitTolerance
        return distance in inner..outer
    }

    /** 把画布坐标换算成「从 12 点起顺时针」的角度，0f..360f */
    fun clockwiseAngle(offset: Offset): Float {
        val degrees = Math.toDegrees(
            atan2((offset.y - center.y).toDouble(), (offset.x - center.x).toDouble())
        ).toFloat()
        return (degrees + 90f + 360f) % 360f
    }

    companion object {
        /**
         * 圆环尽量大，但要给左右两条标签栏留出固定宽度（[sideReserve]），
         * 否则水平引线会伸进圆环的横向范围里。
         */
        fun of(
            size: Size,
            ringWidth: Float,
            radialStub: Float,
            sideReserve: Float,
            labelPad: Float,
            hitTolerance: Float
        ): DonutMetrics {
            val maxByWidth = (size.width / 2f - sideReserve - ringWidth / 2f).coerceAtLeast(24f)
            val maxByHeight = size.height / 2f * 0.92f - ringWidth / 2f
            val ringRadius = min(maxByWidth, maxByHeight)
            val outerRadius = ringRadius + ringWidth / 2f
            return DonutMetrics(
                center = Offset(size.width / 2f, size.height / 2f),
                ringRadius = ringRadius,
                outerRadius = outerRadius,
                stubRadius = outerRadius + radialStub,
                ringWidth = ringWidth,
                labelPad = labelPad,
                hitTolerance = hitTolerance,
                canvasW = size.width,
                canvasH = size.height
            )
        }
    }
}

/**
 * 单个扇区的几何信息。
 *
 * @property edgePoint 扇区外缘中点，径向引出线的起点
 * @property stubPoint 径向引出线终点（环外固定半径处），引线在这里折成水平
 * @property isRight 是否落在右半（`cos >= 0`），决定标签挂在哪一侧
 */
private class SliceGeom(
    val index: Int,
    val startAngle: Float,
    val sweep: Float,
    val cosA: Float,
    val sinA: Float,
    val edgePoint: Offset,
    val stubPoint: Offset,
    val isRight: Boolean
)

/**
 * 一个标签的位置。
 *
 * @property lineEndX 水平引线的终点 x，紧贴文字内侧
 * @property centerY 文字垂直中心，同时也是水平引线的高度
 */
private data class LabelGeom(
    val index: Int,
    val layout: TextLayoutResult,
    val isRight: Boolean,
    /** 是否位于圆心上半：标签不得越过圆心水平线，否则会折到别的象限去 */
    val isTop: Boolean,
    val textX: Float,
    val lineEndX: Float,
    val centerY: Float
)

/**
 * 环形图（自绘）：圆环 + 径向/水平引线标签 + 圆心总额 + 扇区点击高亮。
 *
 * 为什么不用 Vico：这一版要的是「引线 + 名称 + 占比」的外置标签与圆心文字，
 * Vico 的饼图更偏向纯图形，硬套要绕过它自己的标签布局；而这里只有十几个扇区，
 * Canvas 自绘的代码量与可控性都更划算。
 *
 * 引线形状固定为**两段**：先沿扇区角度径向引出到环外，再水平（角度 0）进入标签；
 * 标签高度由扇区角度决定，并被约束在**引线所属象限**内（见 [layoutLabels]）。
 *
 * 标签并非全部常显：占比低于 [MIN_LABEL_RATIO] 的扇区默认不标注，
 * 点击选中后才出现标签与引线，避免小扇区把圆外挤满。
 *
 * 文字用 [rememberTextMeasurer] 量好再画，避免中文字宽估算偏差导致标签出界。
 *
 * @param slices 扇区（按金额降序）
 * @param centerTitle 圆心主文案（如金额）
 * @param centerCaption 圆心副文案（如「共支出(元)」）
 * @param selectedIndex 当前高亮的扇区下标，-1 表示无高亮
 * @param onSliceClick 点击扇区回调，传入下标；调用方负责「再次点击取消高亮」
 * @param modifier 外部修饰符
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    centerTitle: String,
    centerCaption: String,
    selectedIndex: Int = -1,
    onSliceClick: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()

    // 颜色必须在 Composable 作用域取好：AppTheme.color 是 @Composable，DrawScope 里调不了
    val labelColor = AppTheme.color.onSurfaceVariant
    val selectedLabelColor = AppTheme.color.onSurface
    val guideColor = AppTheme.color.outline

    val ringWidth = 30.dp
    val radialStub = 14.dp
    /** 标签文字贴画布边缘的距离（越小越靠边），同时也决定引线到文字的间隙（取其一半） */
    val labelPad = 4.dp
    /** 点击容差（px 在下面换算）；高亮不再炸开，仅用于放宽命中范围 */
    val hitTolerance = 8.dp
    val minLabelGap = 6.dp
    val sideReserve = 84.dp
    val guideWidth = 1.dp

    val labelStyleNormal = TextStyle(color = labelColor, fontSize = 11.sp)
    val labelStyleSelected = TextStyle(
        color = selectedLabelColor,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold
    )

    Box(
        modifier = modifier.pointerInput(slices, sideReserve, ringWidth, hitTolerance) {
            detectTapGestures { offset ->
                val metrics = DonutMetrics.of(
                    size = Size(size.width.toFloat(), size.height.toFloat()),
                    ringWidth = ringWidth.toPx(),
                    radialStub = radialStub.toPx(),
                    sideReserve = sideReserve.toPx(),
                    labelPad = labelPad.toPx(),
                    hitTolerance = hitTolerance.toPx()
                )
                if (!metrics.isOnRing(offset)) return@detectTapGestures

                val angle = metrics.clockwiseAngle(offset)
                var accumulated = 0f
                slices.forEachIndexed { index, slice ->
                    val sweep = slice.ratio * FULL_SWEEP
                    if (angle in accumulated..(accumulated + sweep)) {
                        onSliceClick(index)
                        return@detectTapGestures
                    }
                    accumulated += sweep
                }
            }
        }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val metrics = DonutMetrics.of(
                size = size,
                ringWidth = ringWidth.toPx(),
                radialStub = radialStub.toPx(),
                sideReserve = sideReserve.toPx(),
                labelPad = labelPad.toPx(),
                hitTolerance = hitTolerance.toPx()
            )
            val geoms = buildSliceGeoms(slices, metrics)

            geoms.forEach { geom -> drawSlice(geom, slices, metrics, selectedIndex) }

            val labels = layoutLabels(
                geoms = geoms,
                slices = slices,
                measurer = measurer,
                normalStyle = labelStyleNormal,
                selectedStyle = labelStyleSelected,
                selectedIndex = selectedIndex,
                metrics = metrics,
                minGap = minLabelGap.toPx()
            )
            val guideStroke = guideWidth.toPx()
            labels.forEach { label ->
                drawLeader(
                    label = label,
                    geom = geoms[label.index],
                    metrics = metrics,
                    guideColor = guideColor,
                    sliceColor = slices[label.index].color,
                    selectedIndex = selectedIndex,
                    guideStroke = guideStroke
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = centerTitle,
                style = AppTheme.typography.titleLarge.copy(fontSize = 18.sp),
                color = AppTheme.color.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = centerCaption,
                style = AppTheme.typography.labelSmall,
                color = AppTheme.color.onSurfaceVariant
            )
        }
    }
}

/** 按扇区顺序铺开角度，算出每个扇区的起止角与引线两端点 */
private fun buildSliceGeoms(slices: List<DonutSlice>, metrics: DonutMetrics): List<SliceGeom> {
    var startAngle = START_ANGLE
    return slices.mapIndexed { index, slice ->
        val sweep = slice.ratio * FULL_SWEEP
        val midAngle = startAngle + sweep / 2f
        val radians = Math.toRadians(midAngle.toDouble())
        val cosA = cos(radians).toFloat()
        val sinA = sin(radians).toFloat()
        val unit = Offset(cosA, sinA)
        val geom = SliceGeom(
            index = index,
            startAngle = startAngle,
            sweep = sweep,
            cosA = cosA,
            sinA = sinA,
            edgePoint = metrics.center + unit * metrics.outerRadius,
            stubPoint = metrics.center + unit * metrics.stubRadius,
            isRight = cosA >= 0f
        )
        startAngle += sweep
        geom
    }
}

/**
 * 画一个扇区。
 *
 * 高亮采用**原地加粗**：圆心与半径都不动，只是把这一段的环沿径向加厚（内外各扩一半）。
 * 之前是「沿中角方向整体向外炸开」，那样扇区会脱离圆环、与引线和相邻扇区拉开距离，
 * 视觉上像弹出来一块；原地加粗则始终贴合圆环，只是变粗一档。
 */
private fun DrawScope.drawSlice(
    geom: SliceGeom,
    slices: List<DonutSlice>,
    metrics: DonutMetrics,
    selectedIndex: Int
) {
    if (geom.sweep <= 0f) return
    val strokeWidth = if (geom.index == selectedIndex) {
        metrics.ringWidth * SELECTED_WIDTH_FACTOR
    } else {
        metrics.ringWidth
    }
    drawArc(
        color = slices[geom.index].color,
        startAngle = geom.startAngle,
        // 留一道缝隙，相邻扇区不会糊在一起
        sweepAngle = (geom.sweep - 1.2f).coerceAtLeast(0.6f),
        useCenter = false,
        topLeft = Offset(
            metrics.center.x - metrics.ringRadius,
            metrics.center.y - metrics.ringRadius
        ),
        size = Size(metrics.ringRadius * 2f, metrics.ringRadius * 2f),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
    )
}

/**
 * 排布引线标签。三条规则：
 * 1. **标签留在引线所属象限**：上半的标签不越过圆心水平线，下半同理。
 *    否则第一象限引出的线会把标签折到左下角，看图的人得满屏找对应关系；
 * 2. **同象限内不重叠**：先按角度定位，再做最小间距防撞（见 [resolveVerticalOverlap]）；
 * 3. **默认只标注占比 ≥ [MIN_LABEL_RATIO] 的扇区**，小扇区点击后才出现标签，
 *    避免一堆小扇区的引线糊成一团。
 */
private fun layoutLabels(
    geoms: List<SliceGeom>,
    slices: List<DonutSlice>,
    measurer: TextMeasurer,
    normalStyle: TextStyle,
    selectedStyle: TextStyle,
    selectedIndex: Int,
    metrics: DonutMetrics,
    minGap: Float
): List<LabelGeom> {
    val drafts = geoms
        .filter { it.sweep > 0f }
        // 小扇区默认不标注；选中的那个即使占比很小也要显示
        .filter { it.index == selectedIndex || slices[it.index].ratio >= MIN_LABEL_RATIO }
        .map { geom ->
            val layout = measurer.measure(
                text = "${slices[geom.index].name} ${PercentFormatter.of(slices[geom.index].ratio)}",
                style = if (geom.index == selectedIndex) selectedStyle else normalStyle
            )
            val halfHeight = layout.size.height / 2f
            val isRight = geom.cosA >= 0f
            // 屏幕坐标系 y 轴向下：sin < 0 表示在圆心上方
            val isTop = geom.sinA < 0f

            // 标签中心限制在自己那一半里（上下边界各留出一个半行高）
            val minCenterY = if (isTop) halfHeight else metrics.center.y + halfHeight
            val maxCenterY = if (isTop) {
                (metrics.center.y - halfHeight).coerceAtLeast(minCenterY)
            } else {
                (metrics.canvasH - halfHeight).coerceAtLeast(minCenterY)
            }
            val centerY = geom.stubPoint.y.coerceIn(minCenterY, maxCenterY)

            if (isRight) {
                // 右栏文字贴右边缘，水平引线从文字左侧进入
                val textX = (metrics.canvasW - metrics.labelPad - layout.size.width)
                    .coerceAtLeast(metrics.labelPad)
                LabelGeom(
                    index = geom.index,
                    layout = layout,
                    isRight = true,
                    isTop = isTop,
                    textX = textX,
                    lineEndX = textX - metrics.labelPad * 0.5f,
                    centerY = centerY
                )
            } else {
                // 左栏文字贴左边缘，水平引线从文字右侧进入
                LabelGeom(
                    index = geom.index,
                    layout = layout,
                    isRight = false,
                    isTop = isTop,
                    textX = metrics.labelPad,
                    lineEndX = metrics.labelPad + layout.size.width + metrics.labelPad * 0.5f,
                    centerY = centerY
                )
            }
        }
    return resolveVerticalOverlap(drafts, metrics, minGap)
}

/**
 * 同象限纵向防撞。
 *
 * 按「左/右 × 上/下」分成四组**分别**处理：只在同一象限内互相让位，绝不跨象限挪动 ——
 * 跨象限挪动会直接破坏「标签留在自己引线所在象限」这条规则。
 *
 * 做法：先把被压住的标签往下推；若整体被推出该象限的下边界，
 * 再整体回拉、从下到上收紧，保证顺序与最小间距都不被破坏。
 */
private fun resolveVerticalOverlap(
    drafts: List<LabelGeom>,
    metrics: DonutMetrics,
    minGap: Float
): List<LabelGeom> {
    val result = drafts.toMutableList()

    listOf(true to true, true to false, false to true, false to false)
        .forEach { (isRight, isTop) ->
            val indices = result.indices
                .filter { result[it].isRight == isRight && result[it].isTop == isTop }
                .sortedBy { result[it].centerY }
            if (indices.isEmpty()) return@forEach

            fun minCenterY(half: Float) = if (isTop) half else metrics.center.y + half
            fun maxCenterY(half: Float) =
                (if (isTop) metrics.center.y - half else metrics.canvasH - half)
                    .coerceAtLeast(minCenterY(half))

            fun pushDown() {
                var previousBottom = Float.NEGATIVE_INFINITY
                indices.forEach { index ->
                    val half = result[index].layout.size.height / 2f
                    val lower = (previousBottom + minGap + half).coerceAtLeast(minCenterY(half))
                    val y = result[index].centerY
                        .coerceAtLeast(lower)
                        .coerceAtMost(maxCenterY(half))
                    result[index] = result[index].copy(centerY = y)
                    previousBottom = y + half
                }
            }

            pushDown()

            // 溢出该象限下边界时：整体回拉，再从下到上收紧
            val lastIndex = indices.last()
            val lastHalf = result[lastIndex].layout.size.height / 2f
            val overflow = result[lastIndex].centerY + lastHalf - maxCenterY(lastHalf)
            if (overflow <= 0f) return@forEach

            var nextTop = Float.POSITIVE_INFINITY
            indices.asReversed().forEach { index ->
                val half = result[index].layout.size.height / 2f
                val y = (result[index].centerY - overflow)
                    .coerceAtMost(nextTop - minGap - half)
                    .coerceIn(minCenterY(half), maxCenterY(half))
                result[index] = result[index].copy(centerY = y)
                nextTop = y - half
            }
        }
    return result
}

/**
 * 画一条引线：**径向引出 → 水平（角度 0）进入标签**。
 *
 * 折点取「径向引出末端」与该高度上「圆环外沿」中更外侧的那个，
 * 并严格夹在「径向末端」与「标签终点」之间：
 * 于是两段线都只朝环外方向走，无论标签被防撞推到哪里都不会折回来。
 *
 * 折点正好落在径向末端时（绝大多数情况），径向线已经到头，
 * 就不再折第二次，直接一条斜线连进标签 —— 省掉那个多余的小折角。
 *
 * 高亮扇区的引线用扇区本色并加粗，和标签文字一起高亮。
 */
private fun DrawScope.drawLeader(
    label: LabelGeom,
    geom: SliceGeom,
    metrics: DonutMetrics,
    guideColor: Color,
    sliceColor: Color,
    selectedIndex: Int,
    guideStroke: Float
) {
    if (geom.sweep <= 0f) return

    val highlighted = label.index == selectedIndex
    val lineColor = if (highlighted) sliceColor else guideColor
    val stroke = if (highlighted) guideStroke * SELECTED_STROKE_FACTOR else guideStroke

    // 终点夹取：文字过宽把 lineEndX 顶进环内时，只让引线提前收住，绝不反向折回
    val endX = if (geom.isRight) {
        maxOf(label.lineEndX, geom.stubPoint.x)
    } else {
        minOf(label.lineEndX, geom.stubPoint.x)
    }

    // 折点：既要在该高度的环沿之外（不穿环），又不能越过终点（不回头）
    val dy = label.centerY - metrics.center.y
    val halfChord = sqrt((metrics.outerRadius * metrics.outerRadius - dy * dy).coerceAtLeast(0f))
    val elbowX = if (geom.isRight) {
        maxOf(geom.stubPoint.x, metrics.center.x + halfChord).coerceAtMost(endX)
    } else {
        minOf(geom.stubPoint.x, metrics.center.x - halfChord).coerceAtLeast(endX)
    }

    // ① 沿扇区角度径向引出
    drawLine(lineColor, geom.edgePoint, geom.stubPoint, strokeWidth = stroke)

    if (abs(elbowX - geom.stubPoint.x) <= 1f) {
        // 径向线已经到头，取消折角：一条斜线直接进入标签
        drawLine(lineColor, geom.stubPoint, Offset(endX, label.centerY), strokeWidth = stroke)
    } else {
        // ② 折到标签高度（折点在环沿外，不会穿环）
        drawLine(lineColor, geom.stubPoint, Offset(elbowX, label.centerY), strokeWidth = stroke)
        // ③ 水平（角度 0）进入标签
        drawLine(lineColor, Offset(elbowX, label.centerY), Offset(endX, label.centerY), strokeWidth = stroke)
    }

    drawText(
        textLayoutResult = label.layout,
        topLeft = Offset(label.textX, label.centerY - label.layout.size.height / 2f)
    )
}

/**
 * 子分类构成的亮色调色板。
 *
 * 不能用分类自身的 `colorHex`：子分类的颜色是从父分类继承来的，
 * 「餐饮」下 5 个子分类会是同一个色值，画出来一片同色完全无法区分。
 * 这里按扇区顺序取色，整体偏高明度，避免深色块压住浅色底。
 */
val VIVID_SLICE_COLORS = listOf(
    "#5B8DEF",
    "#63C7F5",
    "#7ED8B8",
    "#FFD166",
    "#FF9F6B",
    "#FF8FA8",
    "#B4A0FF",
    "#8ED95F",
    "#5FD3C4",
    "#FFB4D0",
)

/** 按序号取亮色，超出调色板长度后循环 */
fun vividSliceColor(index: Int): Color {
    val hex = VIVID_SLICE_COLORS[index.mod(VIVID_SLICE_COLORS.size)]
    return runCatching { Color(android.graphics.Color.parseColor(hex)) }
        .getOrDefault(Color(0xFF5B8DEF))
}

/**
 * 按金额降序的**渐变色系**：最大扇区深蓝，依次过渡到浅蓝、青、绿、黄、橙。
 *
 * 对齐统计页截图里「亲子(蓝) → 还款(浅蓝) → 餐饮(青) → 住房(绿) → 购物(黄) → 消费(橙)」
 * 那种从冷到暖的渐变观感；用 HSV 在 222°(蓝)→28°(橙) 之间线性插值，
 * 饱和度高、明度略降，整体鲜艳且相邻扇区有区分度。
 *
 * @param count 扇区数量（与降序后的分类数一致）
 */
fun gradientSliceColors(count: Int): List<Color> {
    if (count <= 0) return emptyList()
    val startHue = 222f // 蓝
    val endHue = 28f   // 橙
    return List(count) { index ->
        val t = if (count == 1) 0f else index.toFloat() / (count - 1)
        val hue = startHue + (endHue - startHue) * t
        val sat = 0.70f
        val value = 0.94f - t * 0.08f // 越靠暖色越略暗，避免橙色刺眼
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
    }
}
