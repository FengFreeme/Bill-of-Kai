package com.kai.bill.feature.settings.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kai.bill.core.design.component.AmountText
import com.kai.bill.core.design.component.EmptyState
import com.kai.bill.core.design.component.ListCard
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.PendingBill
import com.kai.bill.domain.model.PendingReason
import com.kai.bill.domain.model.SourceType

/**
 * 待审核记录页：判不准的通知都在这里等用户拍板。
 *
 * 每条给三样东西 —— **为什么进来**（原因 + 命中的词）、**建议怎么记**（方向 / 是否统计 /
 * 分类 / 账户）、**原文**（万一用户要看原始文案），然后三个动作：
 * 接受建议 / 改一下 / 不要这笔。
 *
 * 没有建议的条目（判不出方向）只留「不要这笔」：既落不了库，也不该让用户点一个
 * 点了没反应的按钮 —— 金额与原文就在眼前，想记可以去「记一笔」手动补录。
 *
 * @param uiState 页面状态
 * @param onAccept 接受某条建议
 * @param onEdit 按建议落库后进入编辑页
 * @param onDiscard 丢弃某条（不产生记录）
 * @param onAcceptAll 全部接受（只处理有建议的条目）
 * @param onClearError 清除错误提示
 * @param onBack 返回
 */
@Composable
fun NeedsReviewScreen(
    uiState: NeedsReviewUiState,
    onAccept: (PendingBill) -> Unit,
    onEdit: (PendingBill) -> Unit,
    onDiscard: (PendingBill) -> Unit,
    onAcceptAll: () -> Unit,
    onClearError: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        ReviewTopBar(
            showAcceptAll = uiState.acceptableCount > 0,
            onAcceptAll = onAcceptAll,
            onBack = onBack
        )

        if (uiState.isEmpty) {
            EmptyState(
                title = "没有待确认的账单",
                subtitle = "判不准的通知会先放到这里，确认之后才成为正式账单",
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.items, key = { it.id }) { item ->
                    PendingRow(
                        item = item,
                        categoryNames = uiState.categoryNames,
                        accountNames = uiState.accountNames,
                        busy = uiState.busy,
                        onAccept = { onAccept(item) },
                        onEdit = { onEdit(item) },
                        onDiscard = { onDiscard(item) }
                    )
                }
            }
        }

        uiState.errorMessage?.let { message ->
            Text(
                text = message,
                color = AppTheme.ext.expense,
                style = AppTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClearError() }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun ReviewTopBar(
    showAcceptAll: Boolean,
    onAcceptAll: () -> Unit,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .padding(8.dp)
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "‹",
                style = AppTheme.typography.headlineLarge,
                color = AppTheme.color.onSurface
            )
        }
        Text(
            text = "待审核记录",
            style = AppTheme.typography.titleLarge,
            color = AppTheme.color.onSurface
        )
        Spacer(modifier = Modifier.weight(1f))
        if (showAcceptAll) {
            // 只在确实有可接受的条目时出现：没有可接受的，点了也没反应
            Text(
                text = "全部接受",
                style = AppTheme.typography.titleSmall,
                color = AppTheme.color.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onAcceptAll)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun PendingRow(
    item: PendingBill,
    categoryNames: Map<Long, String>,
    accountNames: Map<Long, String>,
    busy: Boolean,
    onAccept: () -> Unit,
    onEdit: () -> Unit,
    onDiscard: () -> Unit
) {
    ListCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AmountText(
                text = "¥%.2f".format(item.amountCents / 100.0),
                color = amountColor(item.suggestedType)
            )
            Text(
                text = buildString {
                    append(reasonLabel(item.reason))
                    item.matchedKeyword?.let { append("（因为『").append(it).append("』）") }
                },
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.color.onSurface
            )
            Text(
                text = suggestionLabel(
                    item = item,
                    categoryName = item.suggestedCategoryId?.let { categoryNames[it] },
                    accountName = item.suggestedAccountId?.let { accountNames[it] }
                ),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.color.onSurfaceVariant
            )
            item.rawText?.takeIf { it.isNotBlank() }?.let { raw ->
                Text(
                    text = "原始通知：$raw",
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.color.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (item.hasSuggestion) {
                    RowAction(
                        text = "接受建议",
                        color = AppTheme.color.primary,
                        enabled = !busy,
                        onClick = onAccept
                    )
                    RowAction(
                        text = "改一下",
                        color = AppTheme.color.onSurfaceVariant,
                        enabled = !busy,
                        onClick = onEdit
                    )
                }
                RowAction(
                    text = "不要这笔",
                    color = AppTheme.ext.expense,
                    enabled = !busy,
                    onClick = onDiscard
                )
            }
        }
    }
}

@Composable
private fun RowAction(
    text: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = text,
        style = AppTheme.typography.titleSmall,
        color = if (enabled) color else color.copy(alpha = 0.4f),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

/** 为什么进待确认（人话版，与通知里的措辞保持一致） */
private fun reasonLabel(reason: PendingReason): String = when (reason) {
    PendingReason.DIRECTION_UNKNOWN -> "看不出是支出还是收入"
    PendingReason.SELF_TRANSFER -> "自己的钱搬家，可能是账户互转"
    PendingReason.GIFT -> "红包：收 / 发与是否人情往来因人而异"
    PendingReason.GENERIC_INBOUND -> "可能是收入，也可能只是账户互转"
}

/** 建议怎么记；没有可用建议时说明原因，而不是显示一片空白 */
private fun suggestionLabel(
    item: PendingBill,
    categoryName: String?,
    accountName: String?
): String {
    val type = item.suggestedType ?: return "没有可用建议：填不出类型与分类，只能选择「不要这笔」"
    return buildString {
        append("建议：").append(typeLabel(type))
        if (!item.suggestedCountInStats) append(" · 不计入统计")
        categoryName?.let { append(" · ").append(it) }
        accountName?.let { append(" · ").append(it) }
    }
}

private fun typeLabel(type: BillType): String = when (type) {
    BillType.EXPENSE -> "支出"
    BillType.INCOME -> "收入"
    BillType.TRANSFER -> "转账"
}

/** 金额颜色只作提示，不带正负号：方向还没定，标个「-」反而误导 */
@Composable
private fun amountColor(type: BillType?): Color = when (type) {
    BillType.EXPENSE -> AppTheme.ext.expense
    BillType.INCOME -> AppTheme.ext.income
    BillType.TRANSFER -> AppTheme.ext.neutral
    null -> AppTheme.color.onSurfaceVariant
}

/**
 * 路由：接上 ViewModel。
 *
 * 「改一下」是「先落库拿 id、再跳编辑页」两步，因此需要一个一次性导航信号
 * （[NeedsReviewUiState.editBillId]），消费后立刻清空，避免返回本页时又被弹走。
 */
@Composable
fun NeedsReviewRoute(
    onBack: () -> Unit,
    onEditBill: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NeedsReviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.editBillId) {
        uiState.editBillId?.let { billId ->
            onEditBill(billId)
            viewModel.onEditNavigationHandled()
        }
    }

    NeedsReviewScreen(
        uiState = uiState,
        onAccept = viewModel::accept,
        onEdit = viewModel::edit,
        onDiscard = viewModel::discard,
        onAcceptAll = viewModel::acceptAll,
        onClearError = viewModel::clearError,
        onBack = onBack,
        modifier = modifier
    )
}

@Preview(name = "待审核-浅色", showBackground = true)
@Composable
private fun NeedsReviewScreenPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.LIGHT) {
        NeedsReviewScreen(
            uiState = NeedsReviewUiState(
                items = listOf(
                    PendingBill(
                        id = 1,
                        amountCents = 8850,
                        suggestedType = null,
                        suggestedCategoryId = null,
                        suggestedAccountId = 4,
                        suggestedCountInStats = false,
                        reason = PendingReason.DIRECTION_UNKNOWN,
                        matchedKeyword = null,
                        tradeTimeMillis = 0,
                        source = SourceType.NOTIFICATION,
                        rawText = "您尾号1234账户人民币88.50元",
                        dedupHash = "a",
                        createdAt = 0
                    ),
                    PendingBill(
                        id = 2,
                        amountCents = 10000,
                        suggestedType = BillType.TRANSFER,
                        suggestedCategoryId = 19,
                        suggestedAccountId = 2,
                        suggestedCountInStats = false,
                        reason = PendingReason.SELF_TRANSFER,
                        matchedKeyword = "转出成功",
                        tradeTimeMillis = 0,
                        source = SourceType.NOTIFICATION,
                        rawText = "转出成功 ¥100.00 到账账户 支付宝余额",
                        dedupHash = "b",
                        createdAt = 0
                    )
                ),
                categoryNames = mapOf(19L to "转账"),
                accountNames = mapOf(2L to "支付宝", 4L to "储蓄卡")
            ),
            onAccept = {},
            onEdit = {},
            onDiscard = {},
            onAcceptAll = {},
            onClearError = {},
            onBack = {}
        )
    }
}
