package com.kai.bill.core.design.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode

/**
 * 底部导航项的纯视觉描述 —— 不含路由 / NavController。
 *
 * @property icon [selected] 由容器传入，便于选中态换色
 */
data class KaiBottomBarItem(
    val label: String,
    val icon: @Composable (selected: Boolean) -> Unit
)

/**
 * 纯视觉底部导航栏：只收 [selectedIndex] / [items] / [onItemClick]，不感知导航
 * （导航装配在 `app/navigation/MainBottomBar`）。
 *
 * 纯色皮肤复用 [GlassBottomBar]（历史命名，实现已是不透明 surface）。
 */
@Composable
fun KaiBottomBar(
    selectedIndex: Int,
    items: List<KaiBottomBarItem>,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp
) {
    GlassBottomBar(modifier = modifier, bottomInset = bottomInset) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            GlassBottomBarItem(
                selected = selected,
                onClick = { onItemClick(index) },
                icon = { item.icon(selected) },
                label = {
                    Text(
                        text = item.label,
                        style = AppTheme.typography.labelMedium,
                        color = if (selected) {
                            AppTheme.color.primary
                        } else {
                            AppTheme.color.onSurfaceVariant
                        }
                    )
                }
            )
        }
    }
}

@Preview(name = "KaiBottomBar-浅色", showBackground = true)
@Composable
private fun KaiBottomBarLightPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.LIGHT) {
        KaiBottomBar(
            selectedIndex = 0,
            items = listOf(
                KaiBottomBarItem("首页") { },
                KaiBottomBarItem("统计") { },
                KaiBottomBarItem("设置") { }
            ),
            onItemClick = {}
        )
    }
}
