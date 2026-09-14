package com.kai.bill.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode
import com.kai.bill.domain.model.Account
import com.kai.bill.domain.model.Bill
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.Category
import com.kai.bill.domain.model.SourceType
import com.kai.bill.feature.common.BillAmountText
import com.kai.bill.feature.common.displayName
import com.kai.bill.feature.home.sourceDisplayName
import com.kai.bill.feature.record.components.categoryIconRes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 单条流水列表项（无卡片背景，用于 [DailyBillGroup] 内部）。
 *
 * 样式对齐参考图：左侧分类图标圆片、中间「分类名+来源标签 / 时间｜备注」、右侧金额。
 *
 * @param bill 账单
 * @param category 分类；为 null 时回退显示账单类型
 * @param account 账户；用于生成来源标签，为空时使用 [SourceType] 默认标签
 * @param modifier 外部修饰符
 * @param onClick 可选点击（进入编辑）
 */
@Composable
fun BillItemCard(
    bill: Bill,
    category: Category?,
    account: Account?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val fallback = AppTheme.color.primary
    val accent = remember(category?.colorHex, fallback) {
        runCatching {
            Color(android.graphics.Color.parseColor(category?.colorHex))
        }.getOrDefault(fallback)
    }
    val iconRes = categoryIconRes(category?.icon)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null, onClick = { onClick?.invoke() })
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = category?.name,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = category?.icon?.takeIf { it.isNotBlank() }
                        ?: category?.name?.takeIf { it.isNotBlank() }?.firstOrNull()?.toString()
                        ?: bill.type.displayName,
                    style = AppTheme.typography.titleMedium,
                    color = accent
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = category?.name ?: bill.type.displayName,
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.color.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val tag = account?.name?.takeIf { it.isNotBlank() } ?: bill.source.sourceDisplayName()
                Surface(
                    color = AppTheme.color.surfaceVariant,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = tag,
                        style = AppTheme.typography.labelSmall,
                        color = AppTheme.color.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = listOfNotNull(
                    formatTime(bill.tradeTimeMillis),
                    bill.merchant?.takeIf { it.isNotBlank() } ?: bill.note?.takeIf { it.isNotBlank() }
                ).joinToString(" | "),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.color.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        BillAmountText(
            bill = bill,
            style = AppTheme.typography.bodyLarge
        )
    }
}

private fun formatTime(timeMillis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timeMillis))

@Preview(name = "流水项-浅色", showBackground = true)
@Composable
private fun BillItemCardLightPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.LIGHT) {
        BillItemCard(
            bill = Bill(
                id = 1L,
                amountCents = 2_500L,
                type = BillType.EXPENSE,
                countInStats = true,
                categoryId = 1L,
                accountId = 1L,
                merchant = "肯德基（万象城店）",
                note = "午餐",
                tradeTimeMillis = 1_757_616_000_000L,
                source = SourceType.NOTIFICATION,
                rawText = null,
                dedupHash = "preview",
                createdAt = 1_757_616_000_000L,
                updatedAt = 1_757_616_000_000L
            ),
            category = Category(
                id = 1L,
                name = "餐饮",
                type = BillType.EXPENSE,
                icon = "restaurant",
                colorHex = "#F09340",
                parentId = null,
                sortOrder = 0,
                isSystem = true
            ),
            account = Account(
                id = 1L,
                name = "支付宝",
                icon = null,
                type = com.kai.bill.domain.model.AccountType.ALIPAY,
                sortOrder = 0,
                isArchived = false
            ),
            modifier = Modifier.padding(16.dp)
        )
    }
}
