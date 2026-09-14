package com.kai.bill.feature.stats.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kai.bill.core.common.money.MoneyFormatter
import com.kai.bill.core.design.component.GlassCard
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.domain.model.stats.AccountStat

/**
 * 账户维度区块：各账户金额与占比，含「未指定账户」兜底项。
 *
 * 「未指定账户」由 `ObserveAccountStatsUseCase` 生成（accountId 为 null），
 * 这里不特殊处理、照常渲染 —— 把这部分金额藏起来会让统计对不上总数。
 *
 * @param stats 账户统计（按金额降序）
 * @param modifier 外部修饰符
 */
@Composable
fun AccountStatsSection(
    stats: List<AccountStat>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        stats.forEach { stat ->
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stat.accountName,
                        style = AppTheme.typography.bodyMedium,
                        color = AppTheme.color.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = MoneyFormatter.withSymbol(stat.amountCents),
                            style = AppTheme.typography.bodyMedium,
                            color = AppTheme.color.onSurface
                        )
                        Text(
                            text = "${(stat.ratio.coerceIn(0f, 1f) * 100).toInt()}%",
                            style = AppTheme.typography.labelSmall,
                            color = AppTheme.color.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
