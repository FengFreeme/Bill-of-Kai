package com.kai.bill.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.kai.bill.core.common.money.MoneyFormatter
import com.kai.bill.core.design.component.AmountText
import com.kai.bill.core.design.component.PrimaryCard
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode
import com.kai.bill.feature.home.HomeOverview

/**
 * 累计消费主卡（基于 [PrimaryCard]）：累计消费（大金额）+ 本月支出 / 本月收入 / 本月结余。
 */
@Composable
fun OverviewCard(
    overview: HomeOverview,
    modifier: Modifier = Modifier
) {
    val onPrimary = AppTheme.color.onPrimary
    PrimaryCard(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "累计消费",
                    style = AppTheme.typography.bodyMedium,
                    color = onPrimary.copy(alpha = 0.72f)
                )
                AmountText(
                    text = "${MoneyFormatter.plain(overview.totalExpenseCents)} 元",
                    color = onPrimary,
                    style = AppTheme.typography.headlineLarge.copy(fontSize = 32.sp, lineHeight = 40.sp),
                    modifier = Modifier.padding(top = 12.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricBlock(
                        label = "本月支出",
                        value = MoneyFormatter.withSymbol(overview.monthExpenseCents)
                    )
                    MetricBlock(
                        label = "本月收入",
                        value = MoneyFormatter.withSymbol(overview.monthIncomeCents)
                    )
                }
                // 结余单独一行并右对齐：三项挤在一行时金额很容易被压成换行，
                // 而结余是「支出与收入相抵后的结果」，放在行尾读起来也更像结论
                MetricBlock(
                    label = "本月结余",
                    value = MoneyFormatter.withSymbol(overview.monthBalanceCents),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalAlignment = Alignment.End
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(onPrimary.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "¥",
                    style = AppTheme.typography.titleMedium,
                    color = onPrimary
                )
            }
        }
    }
}

@Composable
private fun MetricBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start
) {
    val onPrimary = AppTheme.color.onPrimary
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = horizontalAlignment
    ) {
        Text(
            text = label,
            style = AppTheme.typography.bodySmall,
            color = onPrimary.copy(alpha = 0.72f)
        )
        AmountText(
            text = value,
            color = onPrimary,
            style = AppTheme.typography.headlineMedium
        )
    }
}

@Preview(name = "概览卡-浅色", showBackground = true)
@Composable
private fun OverviewCardLightPreview() {
    BillOfKaiTheme(palette = AppPalette.SKY, darkMode = DarkMode.LIGHT) {
        OverviewCard(
            overview = HomeOverview(
                totalExpenseCents = 123_450L,
                monthExpenseCents = 37_766L,
                monthIncomeCents = 960_847L
            ),
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(name = "概览卡-深色", showBackground = true)
@Composable
private fun OverviewCardDarkPreview() {
    BillOfKaiTheme(palette = AppPalette.SKY, darkMode = DarkMode.DARK) {
        OverviewCard(
            overview = HomeOverview(
                totalExpenseCents = 0L,
                monthExpenseCents = 0L,
                monthIncomeCents = 0L
            ),
            modifier = Modifier.padding(16.dp)
        )
    }
}
