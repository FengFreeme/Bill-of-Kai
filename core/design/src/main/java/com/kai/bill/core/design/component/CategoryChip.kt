package com.kai.bill.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode

/**
 * 分类标签：左侧色点 + 分类名。
 *
 * 铁律 3：只接收 [Color] 与 [String]，不认识 `Category` 领域模型。
 * 分类实体里的颜色是十六进制字符串，由 `feature` 层转换后传入。
 *
 * @param label 分类名
 * @param dotColor 分类色点的颜色
 * @param selected 是否选中，选中时加一圈主色描边
 * @param onClick 点击回调；为 null 时不可点击
 * @param modifier 外部修饰符
 */
@Composable
fun CategoryChip(
    label: String,
    dotColor: Color,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = modifier
            .background(
                color = AppTheme.color.surfaceVariant,
                shape = shape
            )
            .then(
                if (selected) {
                    Modifier.border(
                        width = 1.dp,
                        color = AppTheme.color.primary,
                        shape = shape
                    )
                } else {
                    Modifier
                }
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = dotColor, shape = CircleShape)
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = AppTheme.color.onSurface
        )
    }
}

@Preview(name = "分类标签", showBackground = true)
@Composable
private fun CategoryChipPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.LIGHT) {
        Row(modifier = Modifier.padding(16.dp)) {
            CategoryChip(label = "餐饮", dotColor = Color(0xFFF2775F), selected = true)
            CategoryChip(
                label = "交通",
                dotColor = Color(0xFF3E8FD8),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
