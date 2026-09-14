package com.kai.bill.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode
import com.kai.bill.core.design.theme.LocalCardAlpha

/** 底部导航高度：比系统默认略高，单手操作时更好点；对外暴露供布局预留空隙 */
val KaiBottomBarHeight = 64.dp

/**
 * 纯色底部导航栏容器。
 *
 * 历史命名保留为 [GlassBottomBar]，实现已改为不透明 `surface`：
 * - 纯色填充，与页面卡片同一套分层语言
 * - 顶部一条细分割线，替代原玻璃高光边
 * - 无半透明、无 blur、无圆角投影
 *
 * @param modifier 外部修饰符
 * @param bottomInset 系统导航栏高度；由本组件在内部一并铺色，而不是让外部再叠一层
 * @param content 导航项
 */
@Composable
fun GlassBottomBar(
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
    content: @Composable RowScope.() -> Unit
) {
    val colors = AppTheme.color
    // 底栏与卡片共用同一透明度：有背景图时一起变透，不再是一块突兀的实色
    val cardAlpha = LocalCardAlpha.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = cardAlpha))
    ) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = colors.outline.copy(alpha = 0.24f)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(KaiBottomBarHeight)
                // 与屏幕左右边距加大，三项更靠中间
                .padding(horizontal = 28.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
        // 系统导航栏区域由**同一层背景**覆盖：若改在外部再叠一层同色，
        // 半透明时两层叠加处会更实，底栏与屏幕底边之间就会出现一条「缝」。
        if (bottomInset > 0.dp) {
            Spacer(modifier = Modifier.fillMaxWidth().height(bottomInset))
        }
    }
}

/**
 * 底部导航项。
 *
 * 不用 Material3 `NavigationBarItem`：其默认涟漪与指示器在纯色底栏上过重。
 * 这里仅靠图标/文字颜色表达选中，点击无阴影/水波纹。
 *
 * @param selected 是否选中
 * @param onClick 点击回调
 * @param icon 图标
 * @param label 文字标签
 * @param modifier 外部修饰符
 */
@Composable
fun RowScope.GlassBottomBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val contentColor =
        if (selected) AppTheme.color.primary else AppTheme.color.onSurfaceVariant

    Column(
        modifier = modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            icon()
            // 图标与文案间距略收紧
            Spacer(modifier = Modifier.height(2.dp))
            label()
        }
    }
}

/**
 * 预览用的图标占位。
 *
 * 刻意不引入 `material-icons-extended` —— 该库会把上千个图标打进 APK，
 * 本项目实际只会用到少量 Tab 图标，使用矢量 drawable 自行绘制。
 */
@Composable
private fun PreviewIconDot(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (selected) AppTheme.color.primary else AppTheme.color.onSurfaceVariant)
    )
}

@Preview(name = "底栏-浅色", showBackground = true)
@Composable
private fun GlassBottomBarLightPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.LIGHT) {
        GlassBottomBar {
            GlassBottomBarItem(
                selected = true,
                onClick = {},
                icon = { PreviewIconDot(selected = true) },
                label = { Text("首页", style = AppTheme.typography.labelMedium) }
            )
            GlassBottomBarItem(
                selected = false,
                onClick = {},
                icon = { PreviewIconDot(selected = false) },
                label = { Text("统计", style = AppTheme.typography.labelMedium) }
            )
            GlassBottomBarItem(
                selected = false,
                onClick = {},
                icon = { PreviewIconDot(selected = false) },
                label = { Text("设置", style = AppTheme.typography.labelMedium) }
            )
        }
    }
}

@Preview(name = "底栏-深色", showBackground = true)
@Composable
private fun GlassBottomBarDarkPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.DARK) {
        GlassBottomBar {
            GlassBottomBarItem(
                selected = true,
                onClick = {},
                icon = { PreviewIconDot(selected = true) },
                label = { Text("首页", style = AppTheme.typography.labelMedium) }
            )
            GlassBottomBarItem(
                selected = false,
                onClick = {},
                icon = { PreviewIconDot(selected = false) },
                label = { Text("统计", style = AppTheme.typography.labelMedium) }
            )
            GlassBottomBarItem(
                selected = false,
                onClick = {},
                icon = { PreviewIconDot(selected = false) },
                label = { Text("设置", style = AppTheme.typography.labelMedium) }
            )
        }
    }
}
