package com.kai.bill.feature.record.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.domain.model.Category
import com.kai.bill.domain.model.CategoryNode

/** 一级 / 二级统一用 4 列网格 */
private const val COLUMNS = 4

/**
 * 二级网格展开/收起与高度过渡的时长。
 *
 * 用 tween + FastOutSlowIn 而非默认弹簧：弹簧收尾拖尾长，会显得「半天才展开」；
 * 300ms 的匀顺曲线既有可感知的过渡，又不会拖沓。
 */
private const val SUB_GRID_ANIM_MILLIS = 300

/**
 * 两级分类选择器（一级网格 + 二级内联展开）。
 *
 * 布局与交互：
 * - 一级分类是 4 列网格；点某个一级即选中它，并在**它所在的那一整行下方**内联展开它的二级网格，
 *   切换一级时二级区随之收起/展开（带高度过渡）；
 * - **再次点击已展开的一级会收回**其二级网格；
 * - **点击二级后，其所属一级保持高亮**（靠「子分类被选中则父分类也高亮」实现），
 *   一级高亮用分类自身色，二级高亮用主题主色，二者区分；
 * - **一级分类自身始终可选中**：历史账单只存了一级 id，父级可选才能保证编辑回填找得到；
 * - 单元格不铺方块背景，只靠图标底色与文字色表达选中，纵向更省空间；
 * - 二级网格末尾的「＋」跳到分类管理，方便当场补一个子分类。
 *
 * 用普通 Row/Column 而非懒列表：本组件嵌在记一笔的 LazyColumn 里，
 * 嵌套纵向懒列表在无限高度约束下会直接崩溃。
 *
 * @param tree 当前类型下的分类（一级 + 其下二级）
 * @param selectedId 实际选中的分类 ID（可能是一级，也可能是二级）
 * @param expandedParentId 当前展开的一级分类 ID；null 表示该级无二级或未展开
 * @param onSelect 选中回调（用于二级或无子分类的一级）
 * @param onParentClick 点击一级：选中并切换展开/收起
 * @param onManageCategory 点击「＋」：跳分类管理；入参是「＋」所在的**一级分类 id**，
 *   分类管理据此定位到对应大类并直接在其下新增，用户不必自己再找一遍
 * @param allowAddCategory 是否在二级网格末尾显示「＋新增」。
 *   确认卡片的悬浮层必须传 `false`：那里点「＋」要跳转分类管理，而**从悬浮层
 *   启动 Activity 属于后台启动**，会被系统静默拦截 —— 留着就是一个点了没反应的死按钮。
 * @param modifier 外部修饰符
 */
@Composable
fun CategoryPicker(
    tree: List<CategoryNode>,
    selectedId: Long?,
    expandedParentId: Long?,
    onSelect: (Long) -> Unit,
    onParentClick: (Long) -> Unit,
    onManageCategory: (Long) -> Unit,
    allowAddCategory: Boolean = true,
    modifier: Modifier = Modifier
) {
    val expandedIndex = remember(tree, expandedParentId) {
        tree.indexOfFirst { it.parent.id == expandedParentId }
    }
    // 没有二级分类的一级不占二级区，避免展开出一片空白
    val expandedChildren = remember(tree, expandedIndex) {
        tree.getOrNull(expandedIndex)?.children.orEmpty()
    }
    /** 当前展开的那个一级分类 id：二级网格里的「＋」要把它带给分类管理 */
    val ownerParentId = remember(tree, expandedIndex) {
        tree.getOrNull(expandedIndex)?.parent?.id
    }
    val rows = remember(tree) { tree.chunked(COLUMNS) }

    Column(
        // 高度变化完全交给子网格的 expandVertically 驱动：
        // 若这里再加 animateContentSize，会与外层高度动画/子网格展开互相追赶，
        // 结果就是卡片和下方内容跟展开动作不同步。
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        rows.forEachIndexed { rowIndex, rowItems ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { node ->
                    val childrenSelected = node.children.any { it.id == selectedId }
                    val parentSelected = node.parent.id == selectedId
                    CategoryCell(
                        category = node.parent,
                        selected = parentSelected || childrenSelected,
                        expanded = node.parent.id == expandedParentId,
                        hasChildren = node.children.isNotEmpty(),
                        onClick = {
                            if (node.children.isNotEmpty()) {
                                onParentClick(node.parent.id)
                            } else {
                                onSelect(node.parent.id)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                // 末行不足时补空位，保持列宽对齐
                repeat(COLUMNS - rowItems.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }

            // 二级网格内联挂在该一级所在行的下方，展开/收起都有高度过渡
            AnimatedVisibility(
                visible = expandedIndex >= 0 &&
                    expandedChildren.isNotEmpty() &&
                    expandedIndex / COLUMNS == rowIndex,
                enter = fadeIn(
                    animationSpec = tween(SUB_GRID_ANIM_MILLIS, easing = FastOutSlowInEasing)
                ) + expandVertically(
                    animationSpec = tween(SUB_GRID_ANIM_MILLIS, easing = FastOutSlowInEasing)
                ),
                exit = fadeOut(
                    animationSpec = tween(SUB_GRID_ANIM_MILLIS, easing = FastOutSlowInEasing)
                ) + shrinkVertically(
                    animationSpec = tween(SUB_GRID_ANIM_MILLIS, easing = FastOutSlowInEasing)
                )
            ) {
                SubCategoryGrid(
                    categories = expandedChildren,
                    selectedId = selectedId,
                    onSelect = onSelect,
                    // 「＋」带着所属大类 id 走，分类管理要定位到它
                    onManageCategory = { ownerParentId?.let(onManageCategory) },
                    allowAddCategory = allowAddCategory,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/** 一级分类：圆形图标 + 名称；有二级时右下角带小三角提示可展开。 */
@Composable
private fun CategoryCell(
    category: Category,
    selected: Boolean,
    expanded: Boolean,
    hasChildren: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = rememberCategoryAccent(category.colorHex)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            CircleGlyph(
                category = category,
                // 一级高亮用分类自身色；非选中时仅浅色底
                circleColor = if (selected) accent else accent.copy(alpha = 0.18f),
                tint = if (selected) Color.White else accent,
                size = 38.dp,
                iconSize = 20.dp
            )
            if (hasChildren) {
                BadgeTriangle(expanded = expanded)
            }
        }
        Text(
            text = category.name,
            style = AppTheme.typography.labelMedium,
            color = when {
                selected -> accent
                expanded -> AppTheme.color.primary
                else -> AppTheme.color.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/** 二级网格：末尾追加一个「＋新增」占位（以 null 表示）。 */
@Composable
private fun SubCategoryGrid(
    categories: List<Category>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onManageCategory: () -> Unit,
    allowAddCategory: Boolean,
    modifier: Modifier = Modifier
) {
    // 「＋」只在允许时补位：确认卡片的悬浮层里不能跳转，显示出来就是死按钮
    val slots: List<Category?> = remember(categories, allowAddCategory) {
        if (allowAddCategory) categories + null else categories
    }
    val rows = remember(slots) { slots.chunked(COLUMNS) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        rows.forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { category ->
                    if (category == null) {
                        AddCell(onClick = onManageCategory, modifier = Modifier.weight(1f))
                    } else {
                        SubCell(
                            category = category,
                            selected = category.id == selectedId,
                            onClick = { onSelect(category.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                repeat(COLUMNS - rowItems.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SubCell(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = rememberCategoryAccent(category.colorHex)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        CircleGlyph(
            category = category,
            circleColor = if (selected) AppTheme.color.primary else accent.copy(alpha = 0.14f),
            tint = if (selected) AppTheme.color.onPrimary else accent,
            size = 32.dp,
            iconSize = 16.dp
        )
        Text(
            text = category.name,
            style = AppTheme.typography.labelMedium,
            color = if (selected) AppTheme.color.primary else AppTheme.color.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun AddCell(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(50))
                .background(AppTheme.color.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "＋",
                style = AppTheme.typography.titleSmall,
                color = AppTheme.color.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Text(
            text = "新增",
            style = AppTheme.typography.labelMedium,
            color = AppTheme.color.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/**
 * 圆形图标：优先按 icon key 取自托管 Ionicons，取不到时降级为分类名首字。
 *
 * 降级不显示 `category.icon` 原文 —— 那是图标 key 而非展示文本，直接呈现会冒出一串英文。
 */
@Composable
private fun CircleGlyph(
    category: Category,
    circleColor: Color,
    tint: Color,
    size: Dp,
    iconSize: Dp
) {
    val iconRes = categoryIconRes(category.icon)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(50))
            .background(circleColor),
        contentAlignment = Alignment.Center
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = category.name,
                tint = tint,
                modifier = Modifier.size(iconSize)
            )
        } else {
            Text(
                text = category.name.firstOrNull()?.toString() ?: "·",
                style = AppTheme.typography.titleSmall,
                color = tint,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** 右下角小三角：提示该一级分类下面还有二级，展开时标主题色。 */
@Composable
private fun BadgeTriangle(expanded: Boolean) {
    val color = if (expanded) AppTheme.color.primary else AppTheme.color.onSurfaceVariant
    Canvas(modifier = Modifier.size(7.dp)) {
        drawPath(
            path = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            },
            color = color
        )
    }
}

@Composable
private fun rememberCategoryAccent(colorHex: String): Color {
    val fallback = AppTheme.color.primary
    return remember(colorHex, fallback) {
        runCatching { Color(android.graphics.Color.parseColor(colorHex)) }
            .getOrDefault(fallback)
    }
}
