package com.kai.bill.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode

/**
 * 分区小标题，首页「消费记录」、统计页「分类构成 / 趋势」等处共用。
 *
 * @param text 标题文案
 * @param leadingIcon 可选左侧图标（如时钟）
 */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (leadingIcon != null) {
            leadingIcon()
        }
        Text(
            text = text,
            style = AppTheme.typography.titleMedium,
            color = AppTheme.color.onSurface
        )
    }
}

@Preview(name = "小标题-浅色", showBackground = true)
@Composable
private fun SectionTitleLightPreview() {
    BillOfKaiTheme(palette = AppPalette.SKY, darkMode = DarkMode.LIGHT) {
        SectionTitle(text = "消费记录")
    }
}

@Preview(name = "小标题-深色", showBackground = true)
@Composable
private fun SectionTitleDarkPreview() {
    BillOfKaiTheme(palette = AppPalette.SKY, darkMode = DarkMode.DARK) {
        SectionTitle(
            text = "消费记录",
            leadingIcon = {
                androidx.compose.foundation.layout.Box(modifier = Modifier.size(18.dp))
            }
        )
    }
}
