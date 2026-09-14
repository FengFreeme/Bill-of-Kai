package com.kai.bill.core.design.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode

/**
 * 通用分段切换器 —— 日/月/年筛选、统计维度切换都用它。
 *
 * 做成泛型而非写死「日/月/年」：这样账户维度、预算维度切换都能复用同一个组件。
 *
 * @param T 选项类型，通常是枚举
 * @param items 全部选项
 * @param selected 当前选中项
 * @param onSelect 选中回调
 * @param labelOf 把选项转成显示文本的函数
 * @param modifier 外部修饰符
 */
@Composable
fun <T> SegmentTabs(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    labelOf: (T) -> String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(color = AppTheme.color.surfaceVariant, shape = shape)
            .padding(4.dp)
    ) {
        items.forEach { item ->
            val isSelected = item == selected
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) {
                    AppTheme.color.primary
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                },
                label = "segmentBackground"
            )
            val contentColor = if (isSelected) {
                AppTheme.color.onPrimary
            } else {
                AppTheme.color.onSurfaceVariant
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(color = backgroundColor)
                    .clickable { onSelect(item) }
                    .padding(vertical = 8.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(
                    text = labelOf(item),
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor
                )
            }
        }
    }
}

@Preview(name = "分段切换器-浅色", showBackground = true)
@Composable
private fun SegmentTabsLightPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.LIGHT) {
        Box(Modifier.padding(16.dp)) {
            SegmentTabs(
                items = listOf("日", "月", "年"),
                selected = "月",
                onSelect = {},
                labelOf = { it }
            )
        }
    }
}

@Preview(name = "分段切换器-深色", showBackground = true)
@Composable
private fun SegmentTabsDarkPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.DARK) {
        Box(Modifier.padding(16.dp)) {
            SegmentTabs(
                items = listOf("日", "月", "年"),
                selected = "月",
                onSelect = {},
                labelOf = { it }
            )
        }
    }
}
