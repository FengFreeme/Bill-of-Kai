package com.kai.bill.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kai.bill.core.common.money.MoneyFormatter
import com.kai.bill.core.design.component.ListCard
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode
import com.kai.bill.domain.model.Account
import com.kai.bill.domain.model.Bill
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.Category
import com.kai.bill.domain.model.SourceType
import com.kai.bill.feature.home.DailyGroup
import java.time.LocalDate

/**
 * 单日流水卡片：日期头部 + 当日账单列表。
 *
 * 样式对齐参考图，顶部显示「9月12日 今日  支135.60 收0.00」，
 * 下方为一条或多条 [BillItemCard]，项之间用细线分隔。
 *
 * @param group 单日分组数据
 * @param categories 分类映射
 * @param accounts 账户映射
 * @param onBillClick 点击单条账单回调
 */
@Composable
fun DailyBillGroup(
    group: DailyGroup,
    categories: Map<Long, Category>,
    accounts: Map<Long, Account>,
    onBillClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    ListCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = group.dateLabel,
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.color.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "支${MoneyFormatter.plain(group.expenseCents)}",
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.color.onSurfaceVariant
                    )
                    if (group.incomeCents > 0) {
                        Text(
                            text = "  收${MoneyFormatter.plain(group.incomeCents)}",
                            style = AppTheme.typography.bodySmall,
                            color = AppTheme.color.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(
                color = AppTheme.color.outlineVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            group.bills.forEachIndexed { index, bill ->
                BillItemCard(
                    bill = bill,
                    category = categories[bill.categoryId],
                    account = bill.accountId?.let { accounts[it] },
                    onClick = { onBillClick(bill.id) }
                )
                if (index != group.bills.lastIndex) {
                    HorizontalDivider(
                        color = AppTheme.color.outlineVariant,
                        modifier = Modifier.padding(start = 68.dp, end = 16.dp)
                    )
                }
            }
        }
    }
}

@Preview(name = "单日分组-浅色", showBackground = true)
@Composable
private fun DailyBillGroupLightPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.LIGHT) {
        val category = Category(
            id = 1L,
            name = "餐饮",
            type = BillType.EXPENSE,
            icon = "restaurant",
            colorHex = "#F2775F",
            parentId = null,
            sortOrder = 0,
            isSystem = true
        )
        DailyBillGroup(
            group = DailyGroup(
                date = LocalDate.of(2026, 9, 12),
                dateLabel = "9月12日 今日",
                expenseCents = 13_560L,
                incomeCents = 0L,
                bills = listOf(
                    Bill(
                        id = 1L,
                        amountCents = 1_000L,
                        type = BillType.EXPENSE,
                        countInStats = true,
                        categoryId = 1L,
                        accountId = 1L,
                        merchant = "AI",
                        note = "",
                        tradeTimeMillis = 1_757_616_000_000L,
                        source = SourceType.NOTIFICATION,
                        rawText = null,
                        dedupHash = "1",
                        createdAt = 1_757_616_000_000L,
                        updatedAt = 1_757_616_000_000L
                    ),
                    Bill(
                        id = 2L,
                        amountCents = 3_260L,
                        type = BillType.EXPENSE,
                        countInStats = true,
                        categoryId = 1L,
                        accountId = 1L,
                        merchant = "早午晚餐",
                        note = "淘宝购物",
                        tradeTimeMillis = 1_757_570_000_000L,
                        source = SourceType.NOTIFICATION,
                        rawText = null,
                        dedupHash = "2",
                        createdAt = 1_757_570_000_000L,
                        updatedAt = 1_757_570_000_000L
                    )
                )
            ),
            categories = mapOf(1L to category),
            accounts = mapOf(1L to Account(id = 1L, name = "支付宝", icon = null, type = com.kai.bill.domain.model.AccountType.ALIPAY, sortOrder = 0, isArchived = false)),
            onBillClick = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}
