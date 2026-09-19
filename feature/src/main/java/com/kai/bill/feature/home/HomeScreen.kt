package com.kai.bill.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kai.bill.core.design.component.CtaButton
import com.kai.bill.core.design.component.ListCard
import com.kai.bill.core.design.component.EmptyState
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode
import com.kai.bill.core.common.ext.openNotificationListenerSettings
import com.kai.bill.feature.common.SectionTitle
import com.kai.bill.feature.home.components.BudgetCard
import com.kai.bill.feature.home.components.DailyBillGroup
import com.kai.bill.feature.home.components.HomeHeader
import com.kai.bill.feature.home.components.OverviewCard

/**
 * 首页：今日顶栏 → 累计消费主卡 → 记录消费 → 预算卡 → 消费记录。
 *
 * 无状态：只消费 [HomeUiState]，交互通过回调上抛。
 *
 * @param uiState 首页状态
 * @param onRecordClick 进入记一笔二级页
 * @param onBillClick 点击账单进入编辑
 * @param pendingCount 待确认账单条数；**为 0 时不渲染卡片**（没有待办就不要占位置）
 * @param onReviewClick 进入待审核记录页
 * @param modifier 外部修饰符
 */
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onRecordClick: () -> Unit,
    onBillClick: (Long) -> Unit = {},
    onBudgetClick: () -> Unit = {},
    listenerWarning: Boolean = false,
    onListenerWarningAction: () -> Unit = {},
    pendingCount: Int = 0,
    onReviewClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val budgetProgress = uiState.budgetProgress
    val onPrimary = AppTheme.color.onPrimary
    val onSurfaceVariant = AppTheme.color.onSurfaceVariant

    val categoriesById = rememberCategoryMap(uiState.categories)
    val accountsById = rememberAccountMap(uiState.accounts)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "header") {
            HomeHeader(dateLabel = uiState.todayLabel)
        }

        if (listenerWarning) {
            item(key = "listener_warning") {
                ListenerWarningBanner(onClick = onListenerWarningAction)
            }
        }

        // 待确认与断连预警同属「需要你处理一下」的信息，所以并排放在最上面
        if (pendingCount > 0) {
            item(key = "pending_review") {
                PendingReviewCard(count = pendingCount, onClick = onReviewClick)
            }
        }

        item(key = "overview") {
            OverviewCard(overview = uiState.overview)
        }

        item(key = "record_cta") {
            CtaButton(
                text = "记录消费",
                onClick = onRecordClick,
                leadingIcon = {
                    Box(
                        modifier = Modifier.size(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 14.dp, height = 2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(onPrimary)
                        )
                        Box(
                            modifier = Modifier
                                .size(width = 2.dp, height = 14.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(onPrimary)
                        )
                    }
                }
            )
        }

        if (budgetProgress != null) {
            item(key = "budget") {
                BudgetCard(progress = budgetProgress, onClick = onBudgetClick)
            }
        }

        item(key = "flow_title") {
            SectionTitle(
                text = "消费记录",
                leadingIcon = {
                    Canvas(modifier = Modifier.size(18.dp)) {
                        val stroke = 1.6.dp.toPx()
                        drawCircle(
                            color = onSurfaceVariant,
                            style = Stroke(width = stroke)
                        )
                        val center = Offset(size.width / 2f, size.height / 2f)
                        drawLine(
                            color = onSurfaceVariant,
                            start = center,
                            end = Offset(center.x, center.y - size.minDimension * 0.22f),
                            strokeWidth = stroke,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = onSurfaceVariant,
                            start = center,
                            end = Offset(center.x + size.minDimension * 0.18f, center.y),
                            strokeWidth = stroke,
                            cap = StrokeCap.Round
                        )
                    }
                }
            )
        }

        if (uiState.dailyGroups.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = "还没有消费记录哦",
                    subtitle = "今天花销记账了吗？",
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(AppTheme.color.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "📄",
                                style = AppTheme.typography.headlineMedium
                            )
                        }
                    }
                )
            }
        } else {
            uiState.dailyGroups.forEach { group ->
                item(key = group.date.toString()) {
                    DailyBillGroup(
                        group = group,
                        categories = categoriesById,
                        accounts = accountsById,
                        onBillClick = onBillClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * 监听断连预警横幅（M6 保活/异常排查）：点击跳转去重新开启通知监听。
 */
@Composable
private fun ListenerWarningBanner(onClick: () -> Unit) {
    ListCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "⚠️", style = AppTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "自动记账可能已停止",
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.color.onSurface
                )
                Text(
                    text = "通知监听服务未运行（可能被系统回收）。点此去重新开启，避免漏记。",
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.color.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 待确认账单入口卡片：只在有待确认时出现，点击进待审核记录页。
 *
 * 与 [ListenerWarningBanner] 同样用 [ListCard] 包裹，避免有背景图时卡片直接裸在照片上。
 */
@Composable
private fun PendingReviewCard(count: Int, onClick: () -> Unit) {
    ListCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "🧾", style = AppTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "有 $count 笔待确认",
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.color.onSurface
                )
                Text(
                    text = "判不准的通知确认后才记账，点此去处理。",
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.color.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun rememberCategoryMap(categories: List<com.kai.bill.domain.model.Category>): Map<Long, com.kai.bill.domain.model.Category> {
    return androidx.compose.runtime.remember(categories) {
        categories.associateBy { it.id }
    }
}

@Composable
private fun rememberAccountMap(accounts: List<com.kai.bill.domain.model.Account>): Map<Long, com.kai.bill.domain.model.Account> {
    return androidx.compose.runtime.remember(accounts) {
        accounts.associateBy { it.id }
    }
}

/**
 * 首页路由：负责把 ViewModel 与无状态的 [HomeScreen] 接起来。
 *
 * @param onRecordClick 进入记一笔二级页（由 NavHost 注入）
 * @param onBillClick 点击账单进入编辑
 * @param onReviewClick 进入待审核记录页
 * @param modifier 外部修饰符
 * @param viewModel 由 Hilt 注入
 */
@Composable
fun HomeRoute(
    onRecordClick: () -> Unit,
    onBillClick: (Long) -> Unit = {},
    onBudgetClick: () -> Unit = {},
    onReviewClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listenerWarning by viewModel.captureWarning.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    HomeScreen(
        uiState = uiState,
        onRecordClick = onRecordClick,
        onBillClick = onBillClick,
        onBudgetClick = onBudgetClick,
        listenerWarning = listenerWarning,
        onListenerWarningAction = { context.openNotificationListenerSettings() },
        pendingCount = pendingCount,
        onReviewClick = onReviewClick,
        modifier = modifier
    )
}

@Preview(name = "首页-浅色空态", showBackground = true)
@Composable
private fun HomeScreenLightPreview() {
    BillOfKaiTheme(palette = AppPalette.SKY, darkMode = DarkMode.LIGHT) {
        HomeScreen(uiState = HomeUiState(), onRecordClick = {})
    }
}

@Preview(name = "首页-深色空态", showBackground = true)
@Composable
private fun HomeScreenDarkPreview() {
    BillOfKaiTheme(palette = AppPalette.LILAC, darkMode = DarkMode.DARK) {
        HomeScreen(uiState = HomeUiState(), onRecordClick = {})
    }
}
