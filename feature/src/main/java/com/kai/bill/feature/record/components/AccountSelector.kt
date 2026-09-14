package com.kai.bill.feature.record.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.domain.model.Account

/**
 * 账户选择器（横向 Chip 行）。
 *
 * 展示活跃账户；选中项以主题色实心高亮。默认首个账户由 ViewModel 预选。
 *
 * @param accounts 活跃账户列表
 * @param selectedId 选中的账户 ID
 * @param onSelect 选中回调
 * @param modifier 外部修饰符
 */
@Composable
fun AccountSelector(
    accounts: List<Account>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        accounts.forEach { account ->
            val selected = account.id == selectedId
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (selected) AppTheme.color.primary
                        else AppTheme.color.surfaceVariant
                    )
                    .clickable { onSelect(account.id) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val iconRes = accountIconRes(account.icon)
                if (iconRes != null) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = account.name,
                        tint = if (selected) AppTheme.color.onPrimary else AppTheme.color.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text(
                        text = account.icon ?: "·",
                        style = AppTheme.typography.titleMedium,
                        color = if (selected) AppTheme.color.onPrimary else AppTheme.color.onSurface
                    )
                }
                Text(
                    text = account.name,
                    style = AppTheme.typography.labelLarge,
                    color = if (selected) AppTheme.color.onPrimary else AppTheme.color.onSurface
                )
            }
        }
    }
}
