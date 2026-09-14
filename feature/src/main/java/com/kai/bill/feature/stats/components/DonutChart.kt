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
import com.kai.bill.core.design.theme.AppTheme
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
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
    val explode: Float,
    val canvasW: Float,
    val canvasH: Float
) {
    /** 点击是否落在圆环（含高亮炸开）范围内 */
    fun isOnRing(offset: Offset): Boolean {
        val dx = offset.x - center.x
        val dy = offset.y - center.y
        val distance = sqrt(dx * dx + dy * dy)
        val inner = ringRadius - ringWidth / 2f - explode
        val outer = ringRadius + ringWidth / 2f + explode
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
            explode: Float
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
                explode = explode,
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
 * 标签高度由扇区角度决定，不做额外的角度重排（见 [layoutLabels]）。
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
    val labelPad = 6.dp
    val explode = 8.dp
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
        modifier = modifier.pointerInput(slices, sideReserve, ringWidth, explode) {
            detectTapGestures { offset ->
                val metrics = DonutMetrics.of(
                    size = Size(size.width.toFloat(), size.height.toFloat()),
                    ringWidth = ringWidth.toPx(),
                    radialStub = radialStub.toPx(),
                    sideReserve = sideReserve.toPx(),
                    labelPad = labelPad.toPx(),
                    explode = explode.toPx()
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
                explode = explode.toPx()
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

/** 画一个扇区；高亮扇区整体沿中角方向向外炸开 */
private fun DrawScope.drawSlice(
    geom: SliceGeom,
    slices: List<DonutSlice>,
    metrics: DonutMetrics,
    selectedIndex: Int
) {
    if (geom.sweep <= 0f) return
    val offset = if (geom.index == selectedIndex) {
        Offset(geom.cosA * metrics.explode, geom.sinA * metrics.explode)
    } else {
        Offset.Zero
    }
    drawArc(
        color = slices[geom.index].color,
        startAngle = geom.startAngle,
        // 留一道缝隙，相邻扇区不会糊在一起
        sweepAngle = (geom.sweep - 1.2f).coerceAtLeast(0.6f),
        useCenter = false,
        topLeft = Offset(
            metrics.center.x - metrics.ringRadius + offset.x,
            metrics.center.y - metrics.ringRadius + offset.y
        ),
        size = Size(metrics.ringRadius * 2f, metrics.ringRadius * 2f),
        style = Stroke(width = metrics.ringWidth, cap = StrokeCap.Butt)
    )
}

/**
 * 排布引线标签。
 *
 * **高度完全由扇区角度决定**，不做任何额外的角度重排：
 * `stubPoint.y = cy + sin(midAngle) * stubRadius` 本身就是 `midAngle` 的单调函数，
 * 所以直接拿它当初始高度，同侧顺序天然就是「从上到下」——
 * - 右半（第一、四象限）：角度越大越靠下，即高度随角度增大而降低；
 * - 左半（第二、三象限）：角度越大越靠上，即高度随角度增大而升高。
 *
 * 之后再补一层**最小间距兜底**（[resolveVerticalOverlap]），
 * 只在相邻标签会压在一起时把后面的往下挪，不改变「按角度定位」的整体形态。
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
        .map { geom ->
            val layout = measurer.measure(
                text = "${slices[geom.index].name} ${(slices[geom.index].ratio * 100).roundToInt()}%",
                style = if (geom.index == selectedIndex) selectedStyle else normalStyle
            )
            val halfHeight = layout.size.height / 2f
            val centerY = geom.stubPoint.y.coerceIn(halfHeight, metrics.canvasH - halfHeight)
            if (geom.isRight) {
                // 右栏文字贴右边缘，水平引线从文字左侧进入
                val textX = (metrics.canvasW - metrics.labelPad - layout.size.width)
                    .coerceAtLeast(metrics.labelPad)
                LabelGeom(
                    index = geom.index,
                    layout = layout,
                    isRight = true,
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
                    textX = metrics.labelPad,
                    lineEndX = metrics.labelPad + layout.size.width + metrics.labelPad * 0.5f,
                    centerY = centerY
                )
            }
        }
    return resolveVerticalOverlap(drafts, metrics, minGap)
}

/**
 * 同侧纵向防撞：保持原有顺序，只把被压住的标签往下推；
 * 若整体被推出下边界，再整体上移并从下到上收紧一次。
 */
private fun resolveVerticalOverlap(
    drafts: List<LabelGeom>,
    metrics: DonutMetrics,
    minGap: Float
): List<LabelGeom> {
    val result = drafts.toMutableList()
    listOf(true, false).forEach { rightSide ->
        val indices = result.indices
            .filter { result[it].isRight == rightSide }
            .sortedBy { result[it].centerY }
        if (indices.isEmpty()) return@forEach

        fun pushDown() {
            var previousBottom = Float.NEGATIVE_INFINITY
            indices.forEach { index ->
                val half = result[index].layout.size.height / 2f
                val y = result[index].centerY
                    .coerceAtLeast(previousBottom + minGap + half)
                    .coerceIn(half, metrics.canvasH - half)
                result[index] = result[index].copy(centerY = y)
                previousBottom = y + half
            }
        }

        pushDown()

        val lastIndex = indices.last()
        val overflow = result[lastIndex].centerY +
            result[lastIndex].layout.size.height / 2f -
            (metrics.canvasH - metrics.labelPad)
        if (overflow <= 0f) return@forEach

        // 整体上移溢出量，再从下到上收紧，保证顺序与间距都不被破坏
        var nextTop = Float.POSITIVE_INFINITY
        indices.asReversed().forEach { index ->
            val half = result[index].layout.size.height / 2f
            val y = (result[index].centerY - overflow)
                .coerceAtMost(nextTop - minGap - half)
                .coerceIn(half, metrics.canvasH - half)
            result[index] = result[index].copy(centerY = y)
            nextTop = y - half
        }
    }
    return result
}

/**
 * 画一条引线：**径向引出 → 水平（角度 0）进入标签**。
 *
 * 折点的 x 取「径向引出末端」与该高度上「圆环外沿」中更外侧的那个：
 * 正常情况下径向末端已经在环外，折点就是它，看起来就是径向线直接接一段水平线；
 * 只有标签被防撞推到环的横向范围内时，折点才外移到环沿，保证水平段不穿环。
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

    val dy = label.centerY - metrics.center.y
    val halfChord = sqrt((metrics.outerRadius * metrics.outerRadius - dy * dy).coerceAtLeast(0f))
    val elbowX = if (geom.isRight) {
        maxOf(geom.stubPoint.x, metrics.center.x + halfChord).coerceAtMost(label.lineEndX)
    } else {
        minOf(geom.stubPoint.x, metrics.center.x - halfChord).coerceAtLeast(label.lineEndX)
    }
    val elbow = Offset(elbowX, label.centerY)

    // ① 沿扇区角度径向引出
    drawLine(lineColor, geom.edgePoint, geom.stubPoint, strokeWidth = stroke)
    // ② 折到标签高度（径向末端已在环外时，这一步只是一小段竖直对齐）
    drawLine(lineColor, geom.stubPoint, elbow, strokeWidth = stroke)
    // ③ 水平（角度 0）进入标签
    drawLine(lineColor, elbow, Offset(label.lineEndX, label.centerY), strokeWidth = stroke)

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
