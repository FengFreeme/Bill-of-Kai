package com.kai.bill.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode

/**
 * 首页顶栏：左侧「今日 + 日期」大标题 + 右侧圆形头像占位。
 *
 * 不引入 material-icons；头像用几何剪影，避免额外依赖。
 *
 * @param dateLabel 今日日期文案（如「9月14日」），拼在「今日」之后
 */
@Composable
fun HomeHeader(dateLabel: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "今日 $dateLabel",
            style = AppTheme.typography.headlineLarge.copy(fontSize = 28.sp, lineHeight = 36.sp),
            color = AppTheme.color.onSurface
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(AppTheme.color.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            // 简易头像剪影：头 + 肩
            Box(
                modifier = Modifier.size(22.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(AppTheme.color.primary)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .size(width = 16.dp, height = 10.dp)
                        .clip(CircleShape)
                        .background(AppTheme.color.primary)
                )
            }
        }
    }
}

@Preview(name = "首页顶栏", showBackground = true)
@Composable
private fun HomeHeaderPreview() {
    BillOfKaiTheme(palette = AppPalette.SKY, darkMode = DarkMode.LIGHT) {
        HomeHeader(dateLabel = "9月14日", modifier = Modifier.padding(16.dp))
    }
}
