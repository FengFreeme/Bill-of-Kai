package com.kai.bill.feature.record.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode
import com.kai.bill.feature.R

/**
 * 金额数字键盘，固定在「记一笔」页面底部。
 *
 * 左侧 3 列数字区，右侧窄列：日期键 → `+` → `−` → 收起；退格放在数字区第 4 行，
 * 好让右侧排下两个运算符（四个 56dp 键合计 248dp，与左侧 4 行齐平）。
 *
 * `+` / `−` 用于就地算账（「12+8」），按下后同样走 [onDigit]，由上层拼进金额文本并求值。
 * 纯视觉组件：只把按键事件往外抛，不持有业务状态。
 *
 * @param onDigit 点击数字、小数点或 `+` / `−`（`−` 传出的是 ASCII `-`）
 * @param onDelete 点击删除（退格）
 */
@Composable
fun AmountKeypad(
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onPickDate: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val digitRows = listOf(
        listOf("7", "8", "9"),
        listOf("4", "5", "6"),
        listOf("1", "2", "3")
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 8.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 左侧数字区：3 列 4 行
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            digitRows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { key ->
                        DigitKey(
                            label = key,
                            onClick = { onDigit(key) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            // 第 4 行：. 0 ⌫ —— 退格挪进来，右侧操作列才排得下 + / −
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DigitKey(
                    label = ".",
                    onClick = { onDigit(".") },
                    modifier = Modifier.weight(1f)
                )
                DigitKey(
                    label = "0",
                    onClick = { onDigit("0") },
                    modifier = Modifier.weight(1f)
                )
                DigitKey(
                    label = "⌫",
                    onClick = onDelete,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 右侧操作列：日期 → + → − → 收起。
        // 高度：4 × 56 + 3 × 8 = 248dp，与左侧数字区（同样 4 行 248dp）齐平。
        // 注意：不能用 weight(1f) 占位——父链（Surface→AnimatedVisibility→Row）是 wrap-content、
        // 高度 unbounded，weight 会把占位撑到无穷大，导致键盘被无限拉长。
        Column(
            modifier = Modifier.width(72.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DateKey(
                onClick = onPickDate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            )
            DigitKey(
                label = "+",
                onClick = { onDigit("+") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            )
            DigitKey(
                // 显示用真减号（与 + 等宽对齐），传出去的是 ASCII '-'
                label = "−",
                onClick = { onDigit("-") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            )
            CollapseKey(
                onClick = onCollapse,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            )
        }
    }
}

@Composable
private fun DateKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_calendar),
            contentDescription = "选择日期",
            tint = AppTheme.color.onSurface,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun CollapseKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(AppTheme.color.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "收起",
            textAlign = TextAlign.Center,
            style = AppTheme.typography.titleMedium,
            color = AppTheme.color.onPrimary
        )
    }
}

@Composable
private fun DigitKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .height(56.dp)
            // 先铺满可用宽度，再 clip + clickable，避免删除键因内容窄而出现窄方框涟漪
            .clip(shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            textAlign = TextAlign.Center,
            style = AppTheme.typography.titleLarge,
            color = AppTheme.color.onSurface
        )
    }
}

@Preview(name = "数字键盘", showBackground = true, heightDp = 320)
@Composable
private fun AmountKeypadPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.LIGHT) {
        Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            AmountKeypad(
                onDigit = {},
                onDelete = {},
                onPickDate = {},
                onCollapse = {}
            )
        }
    }
}
