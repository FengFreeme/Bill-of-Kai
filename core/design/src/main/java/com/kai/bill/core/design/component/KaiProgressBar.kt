package com.kai.bill.core.design.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode

/**
 * 预算进度条。
 *
 * @param progress 进度，取值 0f~1f；超出范围会被自动钳制到边界
 * @param overBudget 是否超支。为 true 时填充色变为支出红，是唯一的「警戒」信号
 * @param height 进度条高度，默认 8dp
 * @param modifier 外部修饰符
 */
@Composable
fun KaiProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    overBudget: Boolean = false,
    height: androidx.compose.ui.unit.Dp = 8.dp
) {
    val ext = AppTheme.ext
    val fillColor = if (overBudget) ext.alert else AppTheme.color.primary
    // 深色模式下轨道若用 onSurface 会太亮，这里统一用极低透明度的前景色
    val trackColor = AppTheme.color.onSurface.copy(alpha = 0.08f)

    // 预算变化通常是「记一笔账」之后，带动画能让用户感知到进度在动
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        label = "budgetProgress"
    )

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

@Preview(name = "进度条-正常", showBackground = true)
@Composable
private fun KaiProgressBarNormalPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.LIGHT) {
        KaiProgressBar(progress = 0.62f)
    }
}

@Preview(name = "进度条-超支", showBackground = true)
@Composable
private fun KaiProgressBarOverPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.LIGHT) {
        KaiProgressBar(progress = 1f, overBudget = true)
    }
}
