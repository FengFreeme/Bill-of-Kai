package com.kai.bill.core.design.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode

/**
 * 空态占位：无账单、无统计数据时展示。
 *
 * 刻意做得**轻量**：空态页面本身就应该安静，不抢视觉焦点。
 *
 * @param title 主文案，如「还没有记账记录」
 * @param subtitle 辅助说明，可为 null
 * @param icon 顶部图标，可为 null；由调用方传入以保持零业务耦合
 * @param modifier 外部修饰符
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.height(16.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = AppTheme.color.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.color.onSurfaceVariant.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(name = "空态-浅色", showBackground = true)
@Composable
private fun EmptyStateLightPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.LIGHT) {
        Box {
            EmptyState(
                title = "还没有记账记录",
                subtitle = "记下第一笔，或去「设置」开启账单自动同步"
            )
        }
    }
}

@Preview(name = "空态-深色", showBackground = true)
@Composable
private fun EmptyStateDarkPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.DARK) {
        Box {
            EmptyState(
                title = "还没有记账记录",
                subtitle = "记下第一笔，或去「设置」开启账单自动同步"
            )
        }
    }
}
