package com.kai.bill.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode

/** L1 主卡圆角（文档：primaryCard = 28.dp） */
private val PRIMARY_CARD_RADIUS = 28.dp

/**
 * 主卡 —— 累计消费等 L1 聚合指标容器。
 *
 * 主题色纯色填充 + 大圆角，无渐变 / 阴影 / 描边（边缘对齐 [CtaButton]）；
 * 只认识 Color / Dp / 内容 slot，不认识领域模型。
 */
@Composable
fun PrimaryCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(PRIMARY_CARD_RADIUS)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color = AppTheme.color.primary, shape = shape),
        content = content
    )
}

@Preview(name = "PrimaryCard", showBackground = true)
@Composable
private fun PrimaryCardPreview() {
    BillOfKaiTheme(palette = AppPalette.SKY, darkMode = DarkMode.LIGHT) {
        PrimaryCard(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "累计消费",
                color = AppTheme.color.onPrimary,
                style = AppTheme.typography.titleMedium,
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}
