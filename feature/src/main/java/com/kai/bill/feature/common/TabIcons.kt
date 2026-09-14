package com.kai.bill.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode
import com.kai.bill.feature.R

/** 底部导航图标的统一尺寸，与 Material3 NavigationBar 的图标槽位一致 */
private val TAB_ICON_SIZE = 24.dp

/**
 * 底部导航图标集（Ionicons 矢量资源）。
 *
 * 使用本地 `drawable/ic_*.xml`，不引入 `material-icons-extended`，避免 APK 体积膨胀。
 *
 * - 首页：`ic_home`
 * - 统计：`ic_pie_chart`
 * - 设置：`ic_settings`
 */
@Composable
fun HomeTabIcon(selected: Boolean, modifier: Modifier = Modifier) {
    TabVectorIcon(
        resId = R.drawable.ic_home,
        contentDescription = "首页",
        selected = selected,
        modifier = modifier
    )
}

@Composable
fun StatsTabIcon(selected: Boolean, modifier: Modifier = Modifier) {
    TabVectorIcon(
        resId = R.drawable.ic_pie_chart,
        contentDescription = "统计",
        selected = selected,
        modifier = modifier
    )
}

@Composable
fun SettingsTabIcon(selected: Boolean, modifier: Modifier = Modifier) {
    TabVectorIcon(
        resId = R.drawable.ic_settings,
        contentDescription = "设置",
        selected = selected,
        modifier = modifier
    )
}

@Composable
private fun TabVectorIcon(
    resId: Int,
    contentDescription: String,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    Icon(
        painter = painterResource(id = resId),
        contentDescription = contentDescription,
        tint = iconTint(selected),
        modifier = modifier.size(TAB_ICON_SIZE)
    )
}

/** 选中态用主色、未选中用次级文字色，与底栏导航项配色保持一致 */
@Composable
private fun iconTint(selected: Boolean): Color =
    if (selected) AppTheme.color.primary else AppTheme.color.onSurfaceVariant

@Preview(name = "Tab 图标-浅色", showBackground = true)
@Composable
private fun TabIconsLightPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.LIGHT) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            HomeTabIcon(selected = true)
            StatsTabIcon(selected = false)
            SettingsTabIcon(selected = false)
        }
    }
}

@Preview(name = "Tab 图标-深色", showBackground = true)
@Composable
private fun TabIconsDarkPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.DARK) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            HomeTabIcon(selected = true)
            StatsTabIcon(selected = false)
            SettingsTabIcon(selected = false)
        }
    }
}
