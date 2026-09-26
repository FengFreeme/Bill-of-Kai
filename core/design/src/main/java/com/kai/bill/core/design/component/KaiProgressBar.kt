package com.kai.bill.core.design.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode

/**
 * 色阶中间档：琥珀。
 *
 * 它是**色阶的中间点**，不是「警告」这个业务状态，因此不放进 `ExtendedColors` ——
 * 三种主题下取值相同（与「支出红 / 收入绿」一样属于跨主题恒定的功能色），
 * 定义在这里即可，不必让 6 组主题各写一遍。
 */
private val ProgressCaution = Color(0xFFF5C24B)

/** 琥珀档在进度上的位置：一半用完时开始转向警示色 */
private const val CAUTION_AT = 0.5f

/**
 * 预算进度条。
 *
 * 填充色**随进度百分比连续变化**，一眼就能看出「还剩多少余地」：
 * ```
 *    0%  浅绿（收入绿）  预算宽裕
 *   50%  琥珀            用掉一半，开始留神
 *  100%  鲜红（警示红）  超支
 * ```
 * 两端刻意取主题的**功能色**（[AppTheme.ext] 的 `income` / `alert`）：
 * 换主题、切深浅色时「宽裕＝绿、超支＝红」的直觉不能变，这与 `ExtendedColors` 里
 * 「支出红 / 收入绿」的处理一致；中间档琥珀见 [ProgressCaution]。
 *
 * 颜色与长度都带动画：预算变化通常发生在「记一笔」之后，让进度与颜色一起滑过去，
 * 用户才能感知到「这一笔把进度推到了哪一档」。
 *
 * @param progress 取值 0f~1f；超出会被自动钳制到边界
 * @param overBudget 为 true 时直接钉到色阶末端（鲜红），
 *        保证「超支」永远是同一个最刺眼的颜色，不受进度数值抖动影响
 */
@Composable
fun KaiProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    overBudget: Boolean = false,
    height: Dp = 8.dp
) {
    // WHY: 深色模式下轨道若用 onSurface 会太亮，统一用极低透明度的前景色
    val trackColor = AppTheme.color.onSurface.copy(alpha = 0.08f)

    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        label = "budgetProgress"
    )
    // WHY: 颜色必须跟进度一起过渡，否则进度滑过去了颜色才跳变，会闪一下
    val targetColor = if (overBudget) {
        AppTheme.ext.alert
    } else {
        progressFillColor(animatedProgress)
    }
    val fillColor by animateColorAsState(targetValue = targetColor, label = "budgetProgressColor")

    val shape = RoundedCornerShape(height / 2)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .fillMaxHeight()
                .clip(shape)
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(fillColor, fillColor.copy(alpha = 0.75f))
                    )
                )
        )
    }
}

/**
 * 进度 → 填充色：`绿 →(50%) 琥珀 →(100%) 鲜红` 两段线性过渡。
 *
 * 分两段而非直接插值绿红：RGB 上「绿→红」的中点是脏褐色，用到一半应是「琥珀/黄」，需要中间档。
 */
@Composable
private fun progressFillColor(progress: Float): Color {
    val ext = AppTheme.ext
    return if (progress <= CAUTION_AT) {
        lerp(ext.income, ProgressCaution, progress / CAUTION_AT)
    } else {
        lerp(ProgressCaution, ext.alert, (progress - CAUTION_AT) / (1f - CAUTION_AT))
    }
}

@Preview(name = "进度条-色阶", showBackground = true)
@Composable
private fun KaiProgressBarScalePreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.LIGHT) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            listOf(0.1f, 0.35f, 0.5f, 0.7f, 0.9f, 1f).forEach { value ->
                KaiProgressBar(progress = value)
            }
            KaiProgressBar(progress = 1f, overBudget = true)
        }
    }
}

@Preview(name = "进度条-超支深色", showBackground = true)
@Composable
private fun KaiProgressBarOverDarkPreview() {
    BillOfKaiTheme(palette = AppPalette.LILAC, darkMode = DarkMode.DARK) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            listOf(0.2f, 0.6f, 1f).forEach { value ->
                KaiProgressBar(progress = value)
            }
        }
    }
}
