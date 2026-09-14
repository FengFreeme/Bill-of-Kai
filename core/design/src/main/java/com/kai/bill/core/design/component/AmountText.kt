package com.kai.bill.core.design.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode
import com.kai.bill.core.design.theme.TABULAR_NUMBERS

/**
 * 金额文本。
 *
 * 铁律 3 的落地示例：本组件**只接收 [Color]，不认识任何业务类型**
 * （不知道什么是支出/收入/转账），因此设计系统无需依赖 `:domain`。
 * 「按账单类型决定颜色」的语义封装放在 `feature/common/BillAmountText`。
 *
 * 强制开启等宽数字（tnum）：否则金额列表里的小数点对不齐，视觉上非常廉价。
 *
 * @param text 已格式化好的金额字符串，如 `-¥25.00`
 * @param color 金额颜色，由调用方根据业务语义传入
 * @param style 文本样式，默认 `bodyMedium`
 * @param modifier 外部修饰符
 */
@Composable
fun AmountText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    Text(
        text = text,
        modifier = modifier,
        style = style.copy(fontFeatureSettings = TABULAR_NUMBERS),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Preview(name = "金额文本", showBackground = true)
@Composable
private fun AmountTextPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.LIGHT) {
        androidx.compose.foundation.layout.Column {
            AmountText(text = "-¥1,234.50", color = AppTheme.ext.expense)
            AmountText(text = "+¥8,000.00", color = AppTheme.ext.income)
            AmountText(text = "¥500.00", color = AppTheme.ext.neutral)
        }
    }
}
