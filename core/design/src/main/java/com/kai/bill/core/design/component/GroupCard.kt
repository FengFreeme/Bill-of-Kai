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

/** 分组卡圆角（文档：groupCard = 20.dp） */
private val GROUP_CARD_RADIUS = 20.dp

/**
 * 分组卡 —— 二级指标 / 同类信息块。视觉弱于 [PrimaryCard]，
 * 复用 [GlassCard] 纯色表面皮肤，仅固定圆角层级。
 */
@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(GROUP_CARD_RADIUS),
        onClick = onClick,
        content = content
    )
}

@Preview(name = "GroupCard", showBackground = true)
@Composable
private fun GroupCardPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.LIGHT) {
        GroupCard(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "本月笔数",
                style = AppTheme.typography.titleSmall,
                color = AppTheme.color.onSurface,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
