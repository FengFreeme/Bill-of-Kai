package com.kai.bill.core.design.component

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode

/**
 * 色阶的换挡点：到这之前是「浅绿 → 琥珀」，之后是「琥珀 → 鲜红」。
 *
 * 取 0.75 而非 0.8：月预算花掉四分之三时，这个月往往还剩不少天，
 * 此时就开始转琥珀才有提醒意义；等到八成、九成才变色，用户已经花得差不多了。
 */
private const val WARN_AT = 0.75f

/**
 * 预算进度条。
 *
 * 填充色**不是固定色，而是一条随进度滑动的语义色阶**：
 * `浅绿（余量充足） → 琥珀（开始紧张） → 鲜红（超支）`。
 * 用户不用读文字，扫一眼颜色就知道这个月还剩多少余地。
 *
 * 三个刻意的选择：
 * - **中间锚点不是多余的**：绿直接插值到红，中点会落在发灰的浊橄榄色上
 *   （两者的红绿分量正好对消），看起来像「脏了」而不是「更危险了」；
 *   加一个琥珀锚点，整条色阶才是持续升温的观感；
 * - **色阶与品牌主色无关**：进度条表达的是「余量」这个语义，换配色方案不该改变它的含义
 *   —— 与「红色永远是支出」是同一条设计约定；
 * - **颜色跟着进度一起动**：直接复用同一个 `animatedProgress`，宽度与颜色同步过渡，
 *   不会出现「条子已经涨上去了、颜色还没变」。
 *
 * @param progress 进度，取值 0f~1f；超出范围会被自动钳制到边界
 * @param overBudget 是否超支。为 true 时**直接锁到最红的一端**，不参与渐变 ——
 *   它伴随「预算超支」文案，是唯一的强告警，不该被中间色削弱
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
    // 深色模式下轨道若用 onSurface 会太亮，这里统一用极低透明度的前景色
    val trackColor = AppTheme.color.onSurface.copy(alpha = 0.08f)

    // 预算变化通常是「记一笔账」之后，带动画能让用户感知到进度在动
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        label = "budgetProgress"
    )

    val fillColor = progressRampColor(animatedProgress, overBudget)

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
 * 进度 → 填充色。只在当前主题的扩展色里取锚点，不硬编码任何色值。
 *
 * @param progress 已钳制到 0f~1f 的进度
 * @param overBudget 超支时直接返回鲜红，整条色阶不再参与
 */
@Composable
private fun progressRampColor(progress: Float, overBudget: Boolean): Color {
    val ext = AppTheme.ext
    if (overBudget) return ext.alert
    return if (progress <= WARN_AT) {
        lerp(ext.progressSafe, ext.progressWarn, progress / WARN_AT)
    } else {
        lerp(ext.progressWarn, ext.alert, (progress - WARN_AT) / (1f - WARN_AT))
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

/** 色阶全览：调色时一眼看出换挡点是否顺滑，以及有没有出现发浊的中间色 */
@Preview(name = "进度条-色阶", showBackground = true, heightDp = 320)
@Composable
private fun KaiProgressBarRampPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.LIGHT) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf(0f, 0.25f, 0.5f, 0.7f, 0.85f, 1f).forEach { value ->
                Text(text = "${(value * 100).toInt()}%")
                KaiProgressBar(progress = value)
            }
            Text(text = "超支")
            KaiProgressBar(progress = 1f, overBudget = true)
        }
    }
}
