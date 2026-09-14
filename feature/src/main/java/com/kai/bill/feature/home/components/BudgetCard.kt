package com.kai.bill.feature.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kai.bill.core.common.money.MoneyFormatter
import com.kai.bill.core.design.component.AmountText
import com.kai.bill.core.design.component.GlassCard
import com.kai.bill.core.design.component.KaiProgressBar
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode
import com.kai.bill.domain.model.Budget
import com.kai.bill.domain.model.BudgetPeriod
import com.kai.bill.domain.model.DailyMode
import com.kai.bill.domain.model.stats.BudgetProgress

/**
 * 预算进度卡。
 *
 * 展示顺序按「用户最关心的在最后」排列：剩余额度最大、进度条次之、
 * 今日可用与已花最小 —— 视线自然从大字落到细节。
 *
 * @param progress 预算进度快照
 * @param modifier 外部修饰符
 */
@Composable
fun BudgetCard(
    progress: BudgetProgress,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null, onClick = { onClick?.invoke() })
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "本月预算",
                        style = AppTheme.typography.titleMedium,
                        color = AppTheme.color.onSurface
                    )
                    if (progress.isOverBudget) {
                        val overspentCents = (-progress.monthRemainingCents).coerceAtLeast(0L)
                        Text(
                            text = "预算超支 ${MoneyFormatter.signed(overspentCents, direction = 0)}",
                            style = AppTheme.typography.bodySmall,
                            color = AppTheme.ext.alert
                        )
                    }
                }
                AmountText(
                    text = MoneyFormatter.signed(progress.monthlyBudgetCents, direction = 0),
                    color = AppTheme.color.onSurface,
                    style = AppTheme.typography.headlineMedium
                )
            }

            KaiProgressBar(
                progress = progress.progress,
                overBudget = progress.isOverBudget
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BudgetHint(
                    label = "今日可用",
                    text = MoneyFormatter.signed(
                        progress.todayAvailableCents.coerceAtLeast(0L),
                        direction = 0
                    )
                )
                BudgetHint(
                    label = "今日已花",
                    text = MoneyFormatter.signed(progress.todaySpentCents, direction = 0)
                )
            }
        }
    }
}

@Composable
private fun BudgetHint(label: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = AppTheme.typography.bodySmall,
            color = AppTheme.color.onSurfaceVariant
        )
        AmountText(
            text = text,
            color = AppTheme.color.onSurface,
            style = AppTheme.typography.bodyLarge
        )
    }
}

@Preview(name = "预算卡-正常", showBackground = true)
@Composable
private fun BudgetCardNormalPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.LIGHT) {
        BudgetCard(
            progress = BudgetProgress(
                budget = Budget(
                    categoryId = null,
                    period = BudgetPeriod.MONTHLY,
                    amountCents = 300_000L,
                    startDay = 1,
                    dailyMode = DailyMode.ELASTIC,
                    dailyAmountCents = 0L,
                    carryOver = false,
                    enabled = true
                ),
                monthlyBudgetCents = 300_000L,
                monthSpentCents = 100_000L,
                monthRemainingCents = 200_000L,
                daysRemaining = 10,
                todayAvailableCents = 20_000L,
                todaySpentCents = 2_000L,
                todayLeftCents = 18_000L,
                progress = 0.33f,
                isOverBudget = false
            ),
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(name = "预算卡-超支", showBackground = true)
@Composable
private fun BudgetCardOverPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.DARK) {
        BudgetCard(
            progress = BudgetProgress(
                budget = Budget(
                    categoryId = null,
                    period = BudgetPeriod.MONTHLY,
                    amountCents = 100_000L,
                    startDay = 1,
                    dailyMode = DailyMode.ELASTIC,
                    dailyAmountCents = 0L,
                    carryOver = false,
                    enabled = true
                ),
                monthlyBudgetCents = 100_000L,
                monthSpentCents = 120_000L,
                monthRemainingCents = -20_000L,
                daysRemaining = 5,
                todayAvailableCents = -4_000L,
                todaySpentCents = 0L,
                todayLeftCents = -4_000L,
                progress = 1f,
                isOverBudget = true
            ),
            modifier = Modifier.padding(16.dp)
        )
    }
}
