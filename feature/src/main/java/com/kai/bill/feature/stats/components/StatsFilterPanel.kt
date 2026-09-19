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
 * 分组默认什么都不勾，勾什么由用户自己决定；分类 / 账户两组各自带「全选」与「取消全选」，
 * 一键选满或一键清空（空集合与全选在查询里都表示不限，两种状态都能直接点「查看结果」）。
 *
 * @param categories 全部分类（面板内部按类型过滤）
 * @param accounts 全部账户
 * @param draft 当前草稿
 * @param onTypeSelected 类型选择；null 为「不限」
 * @param onCategoryToggled 分类勾选切换
 * @param onAccountToggled 账户勾选切换
 * @param onUnspecifiedAccountToggled 「未指定账户」勾选切换
 * @param onSelectAllCategories 一键全选分类（当前类型下的一级分类）
 * @param onSelectAllAccounts 一键全选账户（含「未指定账户」）
 * @param onClearAllCategories 取消全选分类（清空勾选）
 * @param onClearAllAccounts 取消全选账户（含「未指定账户」）
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
    onSelectAllCategories: () -> Unit,
    onSelectAllAccounts: () -> Unit,
    onClearAllCategories: () -> Unit,
    onClearAllAccounts: () -> Unit,
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
                        // 面板里的类型是统计页维度的一面镜子（点它会切页面维度），
                        // 少了转账这一项，转账维度打开面板会像「一个都没选」
                        FilterChip(
                            text = "转账",
                            selected = draft.type == BillType.TRANSFER,
                            onClick = { onTypeSelected(BillType.TRANSFER) }
                        )
                    }
                }

                item {
                    // 计数口径与查询一致：分类只算一级，草稿里存的也只有一级 id
                    FilterSection(
                        title = "分类",
                        selectedCount = topLevelCategories.count { it.id in draft.categoryIds },
                        totalCount = topLevelCategories.size,
                        onSelectAll = onSelectAllCategories,
                        onClearAll = onClearAllCategories
                    ) {
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
                    // 「未指定账户」也算账户组的一项，否则「全选 / 取消全选」的计数永远差一个
                    FilterSection(
                        title = "账户",
                        selectedCount = draft.accountIds.size +
                            if (draft.includeUnspecifiedAccount) 1 else 0,
                        totalCount = accounts.size + 1,
                        onSelectAll = onSelectAllAccounts,
                        onClearAll = onClearAllAccounts
                    ) {
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

/**
 * 一组筛选项：标题行 + 可换行的多选标签。
 *
 * 给出 [selectedCount] / [totalCount] 后会挂出「全选 / 取消全选」这一对：
 * 已全选时「全选」置灰、一个都没勾时「取消全选」置灰（点了也不会有任何变化）。
 * 类型那组不需要这对按钮，把 `onSelectAll` / `onClearAll` 留空即可。
 */
@Composable
private fun FilterSection(
    title: String,
    selectedCount: Int = 0,
    totalCount: Int = 0,
    onSelectAll: (() -> Unit)? = null,
    onClearAll: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = AppTheme.typography.titleSmall,
                color = AppTheme.color.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (onSelectAll != null) {
                SectionAction(
                    text = "全选",
                    enabled = selectedCount < totalCount,
                    onClick = onSelectAll
                )
            }
            if (onClearAll != null) {
                SectionAction(
                    text = "取消全选",
                    enabled = selectedCount > 0,
                    onClick = onClearAll
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = { content() }
        )
    }
}

/** 分组标题右侧的文字动作：可点时用主题色，不可点时置灰 */
@Composable
private fun SectionAction(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = text,
        style = AppTheme.typography.labelLarge,
        color = if (enabled) AppTheme.color.primary else AppTheme.color.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
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
