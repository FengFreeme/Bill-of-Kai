package com.kai.bill.core.design.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode
import com.kai.bill.core.design.theme.LocalCardAlpha

/** 按压时缩放到此比例，配合 spring 回弹形成轻触手感 */
private const val PRESSED_SCALE = 0.975f

/**
 * 纯色表面卡片 —— 全 App 最基础的容器组件。
 *
 * 历史命名保留为 [GlassCard]，实现已改为不透明 surface 分组卡：
 * 1. 纯色填充（`surface` / `surfaceVariant`）
 * 2. 无描边、无阴影（边缘效果对齐 [CtaButton]：仅靠圆角色块与背景分层）
 * 3. 按压时缩放到 0.975 并弹性回弹
 *
 * PERF: 无 blur、无阴影、无描边、无半透明渐变，列表内大量使用也不会掉帧。
 *
 * @param shape 卡片形状，默认 24dp 圆角
 * @param strong 是否使用更高对比填充（设置项 / 预览格等需要更「实」的场景）
 * @param onClick 点击回调；为 null 时不可点击
 * @param modifier 外部修饰符
 * @param content 卡片内容
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    strong: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = AppTheme.color
    val cardAlpha = LocalCardAlpha.current
    // strong：用 surfaceVariant 提高对比；普通卡用 surface 纯色分组；
    // alpha 由主题注入，配合自定义背景图实现「卡片半透明」
    val fill = (if (strong) colors.surfaceVariant else colors.surface).copy(alpha = cardAlpha)

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "surfaceCardPress"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(color = fill)
            .then(
                if (onClick != null) {
                    // indication 传 null：卡片自带 scale 反馈，不需要默认水波纹
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            ),
        content = content
    )
}

@Preview(name = "表面卡片-浅色", showBackground = true)
@Composable
private fun GlassCardLightPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.LIGHT) {
        Box(Modifier.padding(24.dp)) {
            GlassCard(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "本月支出 ¥1,234.50",
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.color.onSurface
                )
            }
        }
    }
}

@Preview(name = "表面卡片-深色", showBackground = true)
@Composable
private fun GlassCardDarkPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.DARK) {
        Box(Modifier.padding(24.dp)) {
            GlassCard(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "本月支出 ¥1,234.50",
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.color.onSurface
                )
            }
        }
    }
}
