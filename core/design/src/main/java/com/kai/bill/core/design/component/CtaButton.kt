package com.kai.bill.core.design.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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

/** 主按钮圆角（文档：button = 24.dp） */
private val CTA_RADIUS = 24.dp

/**
 * 主操作按钮（CTA）。
 *
 * @param fillMaxWidth 是否撑满宽度；首页主 CTA 通常为 true
 * @param leadingIcon 左侧图标；首页「记录消费」可传加号
 */
@Composable
fun CtaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillMaxWidth: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = 52.dp),
        shape = RoundedCornerShape(CTA_RADIUS),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppTheme.color.primary,
            contentColor = AppTheme.color.onPrimary,
            disabledContainerColor = AppTheme.color.primary.copy(alpha = 0.38f),
            disabledContentColor = AppTheme.color.onPrimary.copy(alpha = 0.60f)
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = text, style = AppTheme.typography.titleMedium)
        }
    }
}

@Preview(name = "CtaButton", showBackground = true)
@Composable
private fun CtaButtonPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.LIGHT) {
        CtaButton(
            text = "记录消费",
            onClick = {},
            leadingIcon = {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.size(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", color = AppTheme.color.onPrimary, style = AppTheme.typography.titleMedium)
                }
            }
        )
    }
}
