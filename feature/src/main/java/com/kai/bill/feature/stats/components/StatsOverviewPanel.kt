package com.kai.bill.feature.stats.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kai.bill.core.common.money.MoneyFormatter
import com.kai.bill.core.design.component.GlassCard
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.domain.model.stats.Overview

/**
 * 区间概览卡：支出 / 收入 / 结余三列。
 *
 * 结余做成动态取色而非固定主色：为负（入不敷出）时转支出红，
 * 否则用普通文字色 —— 三列全彩会显得很吵，也削弱了红色的警示含义。
 *
 * @param overview 本区间概览
 * @param modifier 外部修饰符
 */
@Composable
fun StatsOverviewPanel(
    overview: Overview,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OverviewItem(
                label = "支出",
                amountCents = overview.expenseCents,
                color = AppTheme.ext.expense
            )
            OverviewItem(
                label = "收入",
                amountCents = overview.incomeCents,
                color = AppTheme.ext.income
            )
            OverviewItem(
                label = "结余",
                amountCents = overview.balanceCents,
                color = if (overview.balanceCents < 0L) {
                    AppTheme.ext.expense
                } else {
                    AppTheme.color.onSurface
                }
            )
        }
    }
}

@Composable
private fun OverviewItem(
    label: String,
    amountCents: Long,
    color: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = AppTheme.typography.labelMedium,
            color = AppTheme.color.onSurfaceVariant
        )
        Text(
            text = MoneyFormatter.withSymbol(amountCents),
            style = AppTheme.typography.titleMedium,
            color = color
        )
    }
}
