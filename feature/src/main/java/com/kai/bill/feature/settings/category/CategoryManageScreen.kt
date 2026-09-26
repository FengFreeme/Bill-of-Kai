package com.kai.bill.feature.settings.category

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kai.bill.core.design.component.ListCard
import com.kai.bill.core.design.component.SegmentTabs
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.Category
import com.kai.bill.domain.model.CategoryNode
import com.kai.bill.feature.record.components.categoryIconRes

private const val SUB_CATEGORY_ANIM_MILLIS = 260

/**
 * 分类管理页：两级分类的增删改。无状态：只消费 [CategoryManageUiState]，
 * 所有写操作回调上抛给 [CategoryManageViewModel]。
 *
 * 预置分类不显示删除入口（DAO 层的 `deleteIfCustom` 也拦了一道），但允许改名。
 *
 * @param onDelete 删除分类（ViewModel 内做预置 / 有子分类的校验）
 * @param focusParentId 进入时要定位的一级分类 ID；0 表示不定位（普通进入）
 */
@Composable
fun CategoryManageScreen(
    uiState: CategoryManageUiState,
    onTypeChange: (BillType) -> Unit,
    onToggleExpand: (Long) -> Unit,
    onRequestAddParent: () -> Unit,
    onRequestAddChild: (Long) -> Unit,
    onRequestRename: (Category) -> Unit,
    onDelete: (Category) -> Unit,
    onSubmitEdit: (String) -> Unit,
    onDismissEdit: () -> Unit,
    onClearError: () -> Unit,
    onBack: () -> Unit,
    focusParentId: Long = 0L,
    modifier: Modifier = Modifier
) {
    val typeTabs = remember { listOf(BillType.EXPENSE, BillType.INCOME) }

    val listState = rememberLazyListState()
    // 带着某个大类进来时（记一笔二级网格的「＋」）把它滚到可见位置：
    // 列表一长，光展开用户也找不到。等分类树到位、算出下标后再滚，且只滚一次。
    var focusScrolled by remember { mutableStateOf(false) }
    LaunchedEffect(focusParentId, uiState.tree) {
        if (focusScrolled || focusParentId <= 0L) return@LaunchedEffect
        val index = uiState.tree.indexOfFirst { it.parent.id == focusParentId }
        if (index >= 0) {
            focusScrolled = true
            listState.animateScrollToItem(index)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
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
                text = "分类管理",
                style = AppTheme.typography.titleLarge,
                color = AppTheme.color.onSurface
            )
        }

        SegmentTabs(
            items = typeTabs,
            selected = uiState.type,
            onSelect = onTypeChange,
            labelOf = { if (it == BillType.EXPENSE) "支出" else "收入" },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            uiState.tree.forEach { node ->
                item(key = "p_${node.parent.id}") {
                    // 一级分类与其二级分类放在**同一张卡片**里：它们属于同一个分类组，
                    // 拆成多张卡会让「上下级关系」变得含糊。
                    ListCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ParentRow(
                                node = node,
                                expanded = node.parent.id in uiState.expandedIds,
                                onToggle = { onToggleExpand(node.parent.id) },
                                onAddChild = { onRequestAddChild(node.parent.id) },
                                onRename = { onRequestRename(node.parent) },
                                onDelete = { onDelete(node.parent) }
                            )
                            AnimatedVisibility(
                                visible = node.parent.id in uiState.expandedIds,
                                enter = fadeIn(
                                    animationSpec = tween(SUB_CATEGORY_ANIM_MILLIS, easing = FastOutSlowInEasing)
                                ) + expandVertically(
                                    animationSpec = tween(SUB_CATEGORY_ANIM_MILLIS, easing = FastOutSlowInEasing)
                                ),
                                exit = fadeOut(
                                    animationSpec = tween(SUB_CATEGORY_ANIM_MILLIS, easing = FastOutSlowInEasing)
                                ) + shrinkVertically(
                                    animationSpec = tween(SUB_CATEGORY_ANIM_MILLIS, easing = FastOutSlowInEasing)
                                )
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    node.children.forEach { child ->
                                        ChildRow(
                                            child = child,
                                            onRename = { onRequestRename(child) },
                                            onDelete = { onDelete(child) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item(key = "add_parent") {
                Text(
                    text = "＋ 新增一级分类",
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.color.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onRequestAddParent)
                        .padding(vertical = 14.dp)
                )
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

    uiState.editing?.let { target ->
        CategoryEditDialog(
            target = target,
            onDismiss = onDismissEdit,
            onConfirm = onSubmitEdit
        )
    }
}

@Composable
private fun ParentRow(
    node: CategoryNode,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAddChild: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 仅「箭头 + 圆点 + 名称」这一片可点击展开 / 收起，
        // 与右侧的 ＋/✎/🗑 操作按钮物理隔离，避免嵌套 clickable 互相吞掉事件
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (node.children.isEmpty()) "" else if (expanded) "▾" else "▸",
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.color.onSurfaceVariant,
                modifier = Modifier.width(12.dp),
                textAlign = TextAlign.Center
            )
            CategoryDot(category = node.parent, size = 32.dp, iconSize = 17.dp)
            Text(
                text = node.parent.name,
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.color.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
        RowAction(text = "＋", onClick = onAddChild)
        RowAction(text = "✎", onClick = onRename)
        if (!node.parent.isSystem) {
            RowAction(text = "🗑", onClick = onDelete)
        }
    }
}

@Composable
private fun ChildRow(
    child: Category,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    // 二级行走「一级所在的同一张卡片」内部，缩进一级以体现从属关系
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Spacer(modifier = Modifier.width(22.dp))
        CategoryDot(category = child, size = 28.dp, iconSize = 15.dp)
        Text(
            text = child.name,
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.color.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        RowAction(text = "✎", onClick = onRename)
        if (!child.isSystem) {
            RowAction(text = "🗑", onClick = onDelete)
        }
    }
}

@Composable
private fun RowAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = AppTheme.typography.titleSmall,
        color = AppTheme.color.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

/** 圆形分类标识：有 Ionicons 就画图标，没有则退化为分类名首字。 */
@Composable
private fun CategoryDot(category: Category, size: Dp, iconSize: Dp) {
    val fallback = AppTheme.color.primary
    val accent = remember(category.colorHex, fallback) {
        runCatching { Color(android.graphics.Color.parseColor(category.colorHex)) }
            .getOrDefault(fallback)
    }
    val iconRes = categoryIconRes(category.icon)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = category.name,
                tint = accent,
                modifier = Modifier.size(iconSize)
            )
        } else {
            Text(
                text = category.name.firstOrNull()?.toString() ?: "·",
                style = AppTheme.typography.labelLarge,
                color = accent,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CategoryEditDialog(
    target: CategoryEditTarget,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(target) {
        mutableStateOf((target as? CategoryEditTarget.Rename)?.category?.name.orEmpty())
    }
    val title = when (target) {
        is CategoryEditTarget.NewParent -> "新增一级分类"
        is CategoryEditTarget.NewChild -> "在「${target.parentName}」下新增"
        is CategoryEditTarget.Rename -> "重命名分类"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("分类名") },
                shape = RoundedCornerShape(12.dp)
            )
        },
        confirmButton = {
            Text(
                text = "保存",
                color = AppTheme.color.primary,
                modifier = Modifier
                    .clickable { onConfirm(text) }
                    .padding(12.dp)
            )
        },
        dismissButton = {
            Text(
                text = "取消",
                color = AppTheme.color.onSurfaceVariant,
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(12.dp)
            )
        }
    )
}

/**
 * 分类管理的路由。
 *
 * @param focusParentId 进入时要定位的一级分类 ID；0 表示不定位
 */
@Composable
fun CategoryManageRoute(
    onBack: () -> Unit,
    focusParentId: Long = 0L,
    modifier: Modifier = Modifier,
    viewModel: CategoryManageViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // 带大类进来时（记一笔二级网格的「＋」），展开它并直接弹「在它下面新增」
    LaunchedEffect(focusParentId) {
        if (focusParentId > 0L) viewModel.focusOn(focusParentId)
    }
    CategoryManageScreen(
        uiState = uiState,
        focusParentId = focusParentId,
        onTypeChange = viewModel::onTypeChange,
        onToggleExpand = viewModel::toggleExpand,
        onRequestAddParent = viewModel::onRequestAddParent,
        onRequestAddChild = viewModel::onRequestAddChild,
        onRequestRename = viewModel::onRequestRename,
        onDelete = viewModel::delete,
        onSubmitEdit = viewModel::submitEdit,
        onDismissEdit = viewModel::dismissEdit,
        onClearError = viewModel::clearError,
        onBack = onBack,
        modifier = modifier
    )
}

@Preview(name = "分类管理-浅色", showBackground = true)
@Composable
private fun CategoryManageScreenLightPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.LIGHT) {
        CategoryManageScreen(
            uiState = CategoryManageUiState(),
            onTypeChange = {},
            onToggleExpand = {},
            onRequestAddParent = {},
            onRequestAddChild = {},
            onRequestRename = {},
            onDelete = {},
            onSubmitEdit = {},
            onDismissEdit = {},
            onClearError = {},
            onBack = {}
        )
    }
}
