package com.kai.bill.feature.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kai.bill.core.design.theme.AppTheme

/**
 * 二级页骨架占位：对齐文档路由树，业务在对应里程碑实现。
 *
 * @param title 页标题
 * @param subtitle 说明（通常含里程碑编号）
 * @param modifier 外部修饰符
 */
@Composable
fun PlaceholderScreen(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = AppTheme.typography.headlineSmall,
                color = AppTheme.color.onSurface
            )
            Text(
                text = subtitle,
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.color.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
