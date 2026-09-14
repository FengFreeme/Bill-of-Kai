package com.kai.bill.core.design.component

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode

/** 列表行圆角，略小于分组卡，适合流水 / 设置项 */
private val LIST_CARD_RADIUS = 16.dp

/**
 * 列表容器卡 —— 流水行、排行项、设置项。
 *
 * 基于 [GlassCard] 纯色表面，圆角略小于分组卡，适合密集列表。
 *
 * @param modifier 外部修饰符
 * @param onClick 可选点击
 * @param content 内容
 */
@Composable
fun ListCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LIST_CARD_RADIUS),
        onClick = onClick,
        content = content
    )
}

@Preview(name = "ListCard", showBackground = true)
@Composable
private fun ListCardPreview() {
    BillOfKaiTheme(palette = AppPalette.LILAC, darkMode = DarkMode.LIGHT) {
        ListCard(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "外卖 · ¥25.00",
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.color.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }
    }
}
