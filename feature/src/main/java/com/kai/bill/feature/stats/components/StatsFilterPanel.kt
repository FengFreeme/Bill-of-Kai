package com.kai.bill.feature.stats.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.domain.model.Account
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.Category
import com.kai.bill.feature.stats.StatsFilterDraft

/**
 * 统计页筛选面板（底部浮层内容）。
 *
 * 只渲染草稿状态 [draft]，**不直接改数据**：勾选项由外部更新草稿，
 * 点「查看结果」才真正应用。这样在面板里反复勾选不会触发任何数据库查询。
 *
 * 分类列表会随「类型」联动：[StatsFilterDraft.type] 为 null 时展示全部分类，
 * 选中支出 / 收入后只展示同类型分类 —— 跨类型选分类在业务上没有意义。
 *
 * @param categories 全部分类（面板内部按类型过滤）
 * @param accounts 全部账户
 * @param draft 当前草稿
 * @param onTypeSelected 类型选择；null 为「不限」
 * @param onCategoryToggled 分类勾选切换
 * @param onAccountToggled 账户勾选切换
 * @param onUnspecifiedAccountToggled 「未指定账户」勾选切换
 * @param onReset 重置草稿
 * @param onApply 应用草稿
 * @param onDismiss 关闭面板
 * @param modifier 外部修饰符
 */
@Composable
fun StatsFilterPanel(
    categories: List<Category>,
    accounts: List<Account>,
    draft: StatsFilterDraft,
    onTypeSelected: (BillType?) -> Unit,
    onCategoryToggled: (Long) -> Unit,
    onAccountToggled: (Long) -> Unit,
    onUnspecifiedAccountToggled: () -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topLevelCategories = remember(categories, draft.type) {
        categories
            .filter { it.parentId == null }
            .let { list ->
                draft.type?.let { type -> list.filter { it.type == type } } ?: list
            }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = AppTheme.color.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 把手：纯装饰，不做拖拽手势（点击遮罩即可关闭，够用且不易误触）
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AppTheme.color.outline)
                    .align(Alignment.CenterHorizontally)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "筛选",
                    style = AppTheme.typography.titleLarge,
                    color = AppTheme.color.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "关闭",
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.color.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item {
                    FilterSection(title = "类型") {
                        FilterChip(
                            text = "支出",
                            selected = draft.type == BillType.EXPENSE,
                            onClick = { onTypeSelected(BillType.EXPENSE) }
                        )
                        FilterChip(
                            text = "收入",
                            selected = draft.type == BillType.INCOME,
                            onClick = { onTypeSelected(BillType.INCOME) }
                        )
                    }
                }

                item {
                    FilterSection(title = "分类") {
                        if (topLevelCategories.isEmpty()) {
                            Text(
                                text = "暂无可选分类",
                                style = AppTheme.typography.bodySmall,
                                color = AppTheme.color.onSurfaceVariant
                            )
                        } else {
                            topLevelCategories.forEach { category ->
                                FilterChip(
                                    text = category.name,
                                    selected = category.id in draft.categoryIds,
                                    onClick = { onCategoryToggled(category.id) }
                                )
                            }
                        }
                    }
                }

                item {
                    FilterSection(title = "账户") {
                        FilterChip(
                            text = "未指定账户",
                            selected = draft.includeUnspecifiedAccount,
                            onClick = onUnspecifiedAccountToggled
                        )
                        accounts.forEach { account ->
                            FilterChip(
                                text = account.name,
                                selected = account.id in draft.accountIds,
                                onClick = { onAccountToggled(account.id) }
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(4.dp)) }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "重置",
                    style = AppTheme.typography.bodyMedium,
                    color = if (draft.isEmpty) {
                        AppTheme.color.onSurfaceVariant
                    } else {
                        AppTheme.color.onSurface
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !draft.isEmpty, onClick = onReset)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                )
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onApply),
                    color = AppTheme.color.primary,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "查看结果",
                        style = AppTheme.typography.titleMedium,
                        color = AppTheme.color.onPrimary,
                        modifier = Modifier.padding(vertical = 12.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = AppTheme.typography.titleSmall,
            color = AppTheme.color.onSurface
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = { content() }
        )
    }
}

@Composable
private fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    Surface(
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick)
            .then(
                if (selected) {
                    Modifier.border(width = 1.5.dp, color = AppTheme.color.primary, shape = shape)
                } else {
                    Modifier
                }
            ),
        color = if (selected) {
            AppTheme.color.primary.copy(alpha = 0.14f)
        } else {
            AppTheme.color.surfaceVariant
        },
        shape = shape
    ) {
        Text(
            text = text,
            style = AppTheme.typography.labelLarge,
            color = if (selected) AppTheme.color.primary else AppTheme.color.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
