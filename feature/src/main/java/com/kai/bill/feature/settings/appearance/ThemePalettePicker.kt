package com.kai.bill.feature.settings.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kai.bill.core.design.component.GlassCard
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode
import com.kai.bill.core.design.theme.ExtendedColors
import com.kai.bill.core.design.theme.paletteSetFor
import androidx.compose.material3.ColorScheme

/**
 * 6 宫格主题预览选择器。
 *
 * 布局：3 套主题（行）× 2 种模式（浅色 / 深色列）= 6 格，点一格同时定下
 * 「配色 + 深浅模式」。FOLLOW_SYSTEM 不进网格 —— 它依赖系统无法在此预览，
 * 单独由 [com.kai.bill.feature.settings.appearance.AppearanceScreen] 的深色模式分段器承载。
 *
 * @param selectedPalette 当前选中的配色
 * @param selectedDarkMode 当前选中的深浅模式
 * @param onSelect 选中某格（同时回传配色与模式）
 * @param modifier 外部修饰符
 */
@Composable
fun ThemePalettePicker(
    selectedPalette: AppPalette,
    selectedDarkMode: DarkMode,
    onSelect: (AppPalette, DarkMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppPalette.entries.forEach { palette ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // weight 必须在 RowScope 内调用，故直接展开两个单元格而非 forEach（后者 lambda 无 RowScope）
                PaletteCell(
                    palette = palette,
                    mode = DarkMode.LIGHT,
                    selected = palette == selectedPalette && DarkMode.LIGHT == selectedDarkMode,
                    onSelect = onSelect,
                    modifier = Modifier.weight(1f)
                )
                PaletteCell(
                    palette = palette,
                    mode = DarkMode.DARK,
                    selected = palette == selectedPalette && DarkMode.DARK == selectedDarkMode,
                    onSelect = onSelect,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PaletteCell(
    palette: AppPalette,
    mode: DarkMode,
    selected: Boolean,
    onSelect: (AppPalette, DarkMode) -> Unit,
    modifier: Modifier = Modifier
) {
    // 取该主题在该模式下的配色，直接渲染成迷你预览，不依赖当前全局主题
    val (scheme, _) = paletteColors(palette, mode)

    GlassCard(
        modifier = modifier.height(132.dp),
        strong = true,
        onClick = { onSelect(palette, mode) }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 纯色预览：背景色铺底 + 主色色块
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(scheme.background)
                ) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(28.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(scheme.primary)
                    )
                }
                Text(
                    text = palette.label,
                    style = AppTheme.typography.bodyMedium,
                    color = scheme.onBackground
                )
                Text(
                    text = mode.label,
                    style = AppTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }

            // 选中态：右上角实心主色圆点，避免引入额外图标依赖。
            // align 处于外层 Box 的 content lambda 内（BoxScope），可直接调用
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(scheme.primary)
                )
            }
        }
    }
}

/**
 * 取某主题在某模式下的配色。
 *
 * [paletteSetFor] 只接收 [AppPalette]，浅/深两套都给齐，由这里按模式挑一份 ——
 * 选择器需要「同一主题、浅与深两个预览」，所以模式选择必须留到此刻。
 */
private fun paletteColors(
    palette: AppPalette,
    mode: DarkMode
): Pair<ColorScheme, ExtendedColors> {
    val set = paletteSetFor(palette)
    return if (mode == DarkMode.DARK) {
        set.darkScheme to set.darkExt
    } else {
        set.lightScheme to set.lightExt
    }
}

@Preview(name = "主题宫格-浅色", showBackground = true)
@Composable
private fun ThemePalettePickerLightPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.LIGHT) {
        ThemePalettePicker(
            selectedPalette = AppPalette.MINT,
            selectedDarkMode = DarkMode.LIGHT,
            onSelect = { _, _ -> },
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(name = "主题宫格-深色", showBackground = true)
@Composable
private fun ThemePalettePickerDarkPreview() {
    BillOfKaiTheme(palette = AppPalette.SKY, darkMode = DarkMode.DARK) {
        ThemePalettePicker(
            selectedPalette = AppPalette.SKY,
            selectedDarkMode = DarkMode.DARK,
            onSelect = { _, _ -> },
            modifier = Modifier.padding(16.dp)
        )
    }
}
