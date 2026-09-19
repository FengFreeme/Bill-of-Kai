package com.kai.bill.feature.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kai.bill.core.common.overlay.ReviewKind
import com.kai.bill.core.design.component.ListCard
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.domain.model.Bill
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.CategoryNode
import com.kai.bill.feature.record.components.CategoryPicker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 卡片滑入时长；滑出略快，退场不该让人等 */
private const val SLIDE_IN_MS = 220
private const val SLIDE_OUT_MS = 180

/** 分类网格的最高高度：分类多时卡片不该顶满全屏，用户要能看见自己刚才那一笔 */
private val PICKER_MAX_HEIGHT = 360.dp

/**
 * 确认卡片的 **Activity 版本**（路由）。
 *
 * 用在「点通知进来」这条路径上：用户点通知属于前台操作，启动 Activity 不受任何限制。
 * 而**自动弹出**那条路径不走这里 —— 它由无障碍悬浮层承载（见 `ReviewCardOverlay`），
 * 因为后台启动 Activity 的官方豁免里没有「无障碍服务」这一条。
 */
@Composable
fun ReviewCardRoute(
    billId: Long,
    kind: ReviewKind,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReviewCardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(billId, kind) { viewModel.load(billId, kind) }
    // closed 由 ViewModel 置位（账单不存在 / 已撤销）——统一在这里收口关闭动作
    LaunchedEffect(state.closed) { if (state.closed) onDismiss() }

    ReviewCardContent(
        bill = state.bill,
        categoryName = state.categoryName,
        kind = state.kind,
        tree = state.tree,
        errorMessage = state.errorMessage,
        onDismiss = onDismiss,
        onCategoryPicked = viewModel::pickCategory,
        onRevoke = viewModel::revoke,
        modifier = modifier
    )
}

/**
 * 确认卡片的**内容**（无状态）。
 *
 * 两个宿主共用它，保证「自动弹出的悬浮卡片」与「点通知打开的页面」长得一模一样：
 * - `ReviewCardRoute`（Activity）
 * - `OverlayReviewCardHost`（无障碍悬浮层）
 *
 * 交互上有三件刻意的事：
 * 1. **从下边缘滑入** —— 卡片是「刚刚发生了一件事」的通知，从底部升起比凭空出现更容易被注意到；
 * 2. **点「改分类」不跳出 App**，而是在同一层里再滑出一张分类卡。
 *    原先的做法是 `startActivity` 打开主界面，但那属于**后台启动 Activity**，
 *    系统会静默拦截（无障碍服务不在豁免名单里），用户看到的就是「点了没反应」；
 * 3. 分类卡**沿用应用内 `CategoryPicker` 的观感**（4 列圆形图标网格 + 二级内联展开），
 *    选中的分类先暂存，**点「确认」才落库** —— 手滑点错不会直接把账单改坏。
 *
 * @param bill 待展示账单；null 表示尚未载入（此时只铺一层遮罩）
 * @param categoryName 已解析好的分类名
 * @param kind 「新建一笔」还是「补分类」—— 决定标题与说明文案
 * @param tree 可选分类（两级结构，已按账单方向过滤）
 * @param errorMessage 上一次操作失败的提示；null 表示正常
 * @param onDismiss 关闭卡片。**「确认无误」与点卡片外都是保留并关闭**（都不会撤销），
 *   只是前者明确表达了「就这样记着」，避免用户把点外部当成取消
 * @param onCategoryPicked 在分类卡里点了「确认」并选中了新的分类
 * @param onRevoke 撤销这一笔
 */
@Composable
fun ReviewCardContent(
    bill: Bill?,
    categoryName: String,
    kind: ReviewKind,
    tree: List<CategoryNode>,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onCategoryPicked: (Long) -> Unit,
    onRevoke: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 挂载后再置 true：直接传 visible = true 不会有进入动画，卡片就会「凭空出现」
    var mounted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { mounted = true }

    var picking by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            // 点卡片外区域 = 关闭（**不改变已记账的结果**，所以不是「取消」）
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        if (bill == null) return@Box

        AnimatedVisibility(
            visible = mounted && !picking,
            enter = slideInVertically(animationSpec = tween(SLIDE_IN_MS)) { it } +
                fadeIn(animationSpec = tween(SLIDE_IN_MS)),
            exit = slideOutVertically(animationSpec = tween(SLIDE_OUT_MS)) { it } +
                fadeOut(animationSpec = tween(SLIDE_OUT_MS))
        ) {
            SummaryCard(
                bill = bill,
                categoryName = categoryName,
                kind = kind,
                errorMessage = errorMessage,
                onEditCategory = { picking = true },
                onConfirm = onDismiss,
                onRevoke = onRevoke
            )
        }

        // 「改分类」后在**同一层里**再滑出一张分类卡；不启动任何 Activity
        AnimatedVisibility(
            visible = picking,
            enter = slideInVertically(animationSpec = tween(SLIDE_IN_MS)) { it } +
                fadeIn(animationSpec = tween(SLIDE_IN_MS)),
            exit = slideOutVertically(animationSpec = tween(SLIDE_OUT_MS)) { it } +
                fadeOut(animationSpec = tween(SLIDE_OUT_MS))
        ) {
            CategoryPickerCard(
                tree = tree,
                currentId = bill.categoryId,
                onConfirm = { categoryId ->
                    picking = false
                    onCategoryPicked(categoryId)
                },
                onBack = { picking = false }
            )
        }
    }
}

/** 摘要卡：告诉用户「记了什么、分到哪一类」，并把「改分类」与「确认无误」作为两个主操作 */
@Composable
private fun SummaryCard(
    bill: Bill,
    categoryName: String,
    kind: ReviewKind,
    errorMessage: String?,
    onEditCategory: () -> Unit,
    onConfirm: () -> Unit,
    onRevoke: () -> Unit
) {
    ListCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            // 吞掉卡片内部的点击，否则点在卡片上也会被外层的「点外部关闭」接走
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = kind.title(),
                style = MaterialTheme.typography.titleMedium,
                color = AppTheme.color.onSurface
            )
            Text(
                text = kind.subtitle(bill.source.reviewSourceLabel()),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.color.onSurfaceVariant
            )

            Text(
                text = bill.signedAmountText(),
                style = MaterialTheme.typography.headlineSmall,
                color = AppTheme.color.onSurface
            )

            InfoRow(
                label = "分类",
                value = categoryName,
                // 分类是唯一「值得改」的字段，因此它本身也是入口
                onClick = onEditCategory
            )
            InfoRow(label = "时间", value = formatCardTime(bill.tradeTimeMillis))

            errorMessage?.let { message ->
                Text(
                    text = message,
                    style = AppTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onEditCategory, modifier = Modifier.weight(1f)) {
                    Text(text = "改分类")
                }
                Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                    Text(text = "确认无误")
                }
            }

            // 撤销是破坏性且低频的动作：降为文字按钮，避免与「改分类」抢注意力、被误触
            TextButton(onClick = onRevoke, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "撤销这笔",
                    style = AppTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Text(
                text = kind.footer(bill.type),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.color.onSurfaceVariant
            )
        }
    }
}

/**
 * 分类卡：**复用应用内的 [CategoryPicker]**，观感与「记一笔」页面完全一致。
 *
 * 与记一笔的唯一差别是**多一步「确认」**：
 * 卡片是「顺手改一下」的场景，手指容易点偏，所以选中的分类先暂存在本组件内，
 * 点了「确认」才交给上层落库 —— 点错一次不会直接把账单改坏（点「返回」即可原样退出）。
 */
@Composable
private fun CategoryPickerCard(
    tree: List<CategoryNode>,
    currentId: Long,
    onConfirm: (Long) -> Unit,
    onBack: () -> Unit
) {
    // 初值就是当前分类：打开就能看到「我现在在哪一类」，而不是一片无选中
    var pendingId by remember(tree) { mutableStateOf<Long?>(currentId) }
    // 默认展开当前分类所属的一级，省掉用户自己找一遍
    var expandedParentId by remember(tree, currentId) {
        mutableStateOf(parentIdOf(tree, currentId) ?: tree.firstOrNull()?.parent?.id)
    }

    ListCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "选择分类",
                style = MaterialTheme.typography.titleMedium,
                color = AppTheme.color.onSurface
            )

            if (tree.isEmpty()) {
                Text(
                    text = "还没有可选分类",
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.color.onSurfaceVariant
                )
            } else {
                // 分类网格用普通 Column（不是懒列表），因此这里套 verticalScroll 限高即可，
                // 不会踩到「嵌套纵向懒列表在无限高度下崩溃」那个坑
                Box(
                    modifier = Modifier
                        .heightIn(max = PICKER_MAX_HEIGHT)
                        .verticalScroll(rememberScrollState())
                ) {
                    CategoryPicker(
                        tree = tree,
                        selectedId = pendingId,
                        expandedParentId = expandedParentId,
                        onSelect = { id ->
                            pendingId = id
                            expandedParentId = parentIdOf(tree, id)
                        },
                        // 与记一笔一致：点一级即选中它，同时切换它的二级展开/收起
                        onParentClick = { id ->
                            pendingId = id
                            expandedParentId = if (expandedParentId == id) null else id
                        },
                        // 悬浮层里点「＋」要跳分类管理（后台启动 Activity 会被拦截），
                        // 所以干脆不显示这个入口，见 allowAddCategory
                        onManageCategory = {},
                        allowAddCategory = false
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text(text = "返回")
                }
                Button(
                    onClick = { pendingId?.let(onConfirm) },
                    enabled = pendingId != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "确认")
                }
            }
        }
    }
}

/** 一行「标签 + 值」；[onClick] 非空时整行可点，并在值后带一个可点的提示符 */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            style = AppTheme.typography.bodySmall,
            color = AppTheme.color.onSurfaceVariant
        )
        Text(
            text = if (onClick == null) value else "$value ›",
            style = AppTheme.typography.bodyMedium,
            color = if (onClick == null) AppTheme.color.onSurface else AppTheme.color.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 某个分类所属的一级分类 id；传进来的 id 本身就可能是一级 */
private fun parentIdOf(tree: List<CategoryNode>, categoryId: Long): Long? =
    tree.firstOrNull { node ->
        node.parent.id == categoryId || node.children.any { it.id == categoryId }
    }?.parent?.id

/** 卡片标题：「凭空补记一笔」与「只补了分类」必须一眼能分辨 */
private fun ReviewKind.title(): String = when (this) {
    ReviewKind.CREATED -> "已自动补记一笔"
    ReviewKind.ENRICHED -> "已补全分类"
}

private fun ReviewKind.subtitle(sourceLabel: String): String = when (this) {
    ReviewKind.CREATED -> "来自$sourceLabel，账单已经记好了"
    ReviewKind.ENRICHED -> "这笔${sourceLabel}已经记过，只更新了分类"
}

private fun ReviewKind.footer(type: BillType): String = when (this) {
    ReviewKind.CREATED -> if (type == BillType.TRANSFER) {
        "已自动记好（转账不计入收支）。不处理就保持这样。"
    } else {
        "已自动记好。不处理就保持这样，分类不对可以改。"
    }
    ReviewKind.ENRICHED -> "只改了分类，金额和时间都没动。"
}

/** 卡片里只显示到分钟：它回答的是「这是哪一笔」，秒级精度在这里没有意义 */
private fun formatCardTime(millis: Long): String =
    if (millis <= 0L) {
        "未知时间"
    } else {
        SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(millis))
    }
