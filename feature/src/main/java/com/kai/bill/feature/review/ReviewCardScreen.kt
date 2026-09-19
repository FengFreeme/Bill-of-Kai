package com.kai.bill.feature.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kai.bill.core.common.overlay.ReviewCardReason
import com.kai.bill.core.design.component.GroupCard
import com.kai.bill.core.design.component.ListCard
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.domain.model.Bill
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.CategoryNode
import com.kai.bill.feature.record.components.CategoryPicker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 确认卡片的 **Activity 版本**（路由）。
 *
 * 用在「点通知进来」这条路径上：用户点通知属于前台操作，启动 Activity 不受任何限制。
 * 而**自动弹出**那条路径不走这里 —— 它由无障碍悬浮层承载（见 `ReviewCardOverlay`），
 * 因为后台启动 Activity 的官方豁免里没有「无障碍服务」这一条。
 *
 * @param billId 要展示的账单主键
 * @param reason 这笔的来由（新建 / 补分类），决定卡片文案
 * @param onDismiss 关闭卡片（不改变已记账的结果）
 */
@Composable
fun ReviewCardRoute(
    billId: Long,
    reason: ReviewCardReason = ReviewCardReason.CREATED,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReviewCardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(billId) { viewModel.load(billId) }
    // closed 由 ViewModel 置位（账单不存在 / 已撤销）——统一在这里收口关闭动作
    LaunchedEffect(state.closed) { if (state.closed) onDismiss() }

    ReviewCardContent(
        bill = state.bill,
        reason = reason,
        categoryName = state.categoryName,
        categoryTree = state.categoryTree,
        onDismiss = onDismiss,
        onSelectCategory = viewModel::changeCategory,
        onRevoke = viewModel::revoke,
        modifier = modifier
    )
}

/**
 * 确认卡片的**内容**。
 *
 * 两个宿主共用它，保证「自动弹出的悬浮卡片」与「点通知打开的页面」长得一模一样：
 * - `ReviewCardRoute`（Activity）
 * - `OverlayCaptureHost`（无障碍悬浮层）
 *
 * 数据靠参数传入而不自己取：悬浮层的宿主是我们自建的 `OverlayViewOwner`
 * （只有 `LifecycleOwner` + `SavedStateRegistryOwner` + `ViewModelStoreOwner` 三个空壳），
 * **拿不到 Hilt 的 ViewModel 工厂**，`hiltViewModel()` 在那里会直接失败；
 * 统一由宿主把分类树与落库动作喂进来，两个宿主才走同一条路。
 *
 * @param bill 待展示账单；null 表示尚未载入（此时只铺一层遮罩）
 * @param reason 这笔的来由（新建 / 补分类）；标题与结尾说明都按它取文案
 * @param categoryName 已解析好的分类名
 * @param categoryTree 当前账单类型下的两级分类，供卡片内弹出的选择器使用
 * @param onDismiss 点卡片外区域 / 关闭
 * @param onSelectCategory 选中新分类；宿主负责落库，卡片本地先乐观更新显示
 * @param onRevoke 撤销这一笔
 */
@Composable
fun ReviewCardContent(
    bill: Bill?,
    reason: ReviewCardReason,
    categoryName: String,
    categoryTree: List<CategoryNode>,
    onDismiss: () -> Unit,
    onSelectCategory: (Long) -> Unit,
    onRevoke: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 卡片上显示的分类名：确认后立即本地改名（乐观），不等仓储回读 ——
    // 卡片是短命的，等一次 IO 再刷新只会让用户觉得「点了没反应」。
    var shownCategoryName by remember(bill?.id, categoryName) { mutableStateOf(categoryName) }
    /**
     * 当前分类 id，本地维护。
     *
     * 不能直接读 `bill.categoryId`：悬浮层那条路径抱着的是**挂窗口那一刻**的账单快照，
     * 改完分类宿主不会重挂窗口（重挂会闪一下），于是快照会一直是旧的。
     */
    var currentCategoryId by remember(bill?.id) { mutableStateOf(bill?.categoryId) }
    /** 分类选择卡片是否弹起 */
    var pickerVisible by remember(bill?.id) { mutableStateOf(false) }
    /** 面板里**暂选**的分类：点「确认」才生效，点「返回」原样退回 */
    var pendingCategoryId by remember(bill?.id) { mutableStateOf<Long?>(null) }
    /** 选择器里展开的一级分类；首次打开会定位到当前分类所属的大类 */
    var expandedParentId by remember(bill?.id) { mutableStateOf<Long?>(null) }

    // 入场动画进度：0 = 整张卡完全藏在屏幕下缘之外，1 = 就位。
    //
    // 用 Animatable 驱动、而不是 AnimatedVisibility，是因为遮罩要和卡片**共用同一个进度**淡入 ——
    // 否则会出现「卡片还在屏幕外、遮罩已经全黑」的割裂感。
    val enter = remember { Animatable(0f) }
    LaunchedEffect(bill) {
        // bill 为 null 时（Activity 版在查库）先按「未入场」钉住，
        // 等数据到位再从屏幕下方滑进来，避免动画在白屏阶段就被消耗掉
        if (bill == null) {
            enter.snapTo(0f)
        } else {
            enter.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = CARD_ENTER_DURATION_MS,
                    easing = FastOutSlowInEasing
                )
            )
        }
    }

    // 卡片自身的像素高度：入场位移要刚好把它藏到屏幕下缘之外
    var cardHeightPx by remember { mutableIntStateOf(0) }
    val bottomMarginPx = with(LocalDensity.current) { CardBottomMargin.roundToPx() }
    // 选择器弹起时把确认卡压暗：视觉上「上面又叠了一张卡」，也暗示底下那张暂时不可点
    val cardAlpha by animateFloatAsState(
        targetValue = if (pickerVisible) DIMMED_CARD_ALPHA else 1f,
        label = "review_card_dim"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SCRIM_ALPHA * enter.value))
            // 点卡片外区域：选择器开着就先收起它，否则关闭整张卡片
            // （关闭卡片**不改变已记账的结果**，所以不是「取消」）
            .clickable { if (pickerVisible) pickerVisible = false else onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        if (bill == null) return@Box

        // 打开分类选择卡片。
        // 每次都把「暂选」重置回当前分类，避免上一次没确认的选择残留；
        // 首次打开还会展开当前分类所属的一级，省得用户自己去找。
        val openPicker = {
            pendingCategoryId = currentCategoryId
            if (expandedParentId == null) {
                expandedParentId = categoryTree.firstOrNull { node ->
                    node.parent.id == currentCategoryId ||
                        node.children.any { it.id == currentCategoryId }
                }?.parent?.id
            }
            pickerVisible = true
        }

        ListCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CardBottomMargin)
                .onSizeChanged { cardHeightPx = it.height }
                // 从**屏幕下缘**滑入：位移恰好等于「卡片高度 + 底部留白」，
                // 即起点时卡片顶端正好贴在屏幕下缘（完全看不见），随后整体上移就位。
                // 用 graphicsLayer 而不是改布局：只影响绘制，不触发重新测量。
                .graphicsLayer {
                    translationY = (1f - enter.value) * (cardHeightPx + bottomMarginPx)
                    alpha = cardAlpha
                }
                // 吞掉卡片内部的点击（否则会被外层的「点外部关闭」接走）；
                // 选择器开着时，点卡片本身等于收起选择器。
                // 用 pickerVisible 作 key 让 pointerInput 重启，避免闭包读到旧状态。
                .pointerInput(pickerVisible) {
                    detectTapGestures { if (pickerVisible) pickerVisible = false }
                }
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    // 「补分类」必须写在标题里：否则用户看到卡片会以为程序又记了一笔
                    text = when (reason) {
                        ReviewCardReason.CREATED -> "已记一笔 · ${bill.source.reviewSourceLabel()}"
                        ReviewCardReason.ENRICHED -> "已补充分类 · ${bill.source.reviewSourceLabel()}"
                    },
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
                    value = shownCategoryName,
                    // 分类是唯一「值得改」的字段，因此它本身也是入口
                    onClick = openPicker
                )
                InfoRow(label = "时间", value = formatCardTime(bill.tradeTimeMillis))
                bill.note?.takeIf { it.isNotBlank() }?.let { note ->
                    InfoRow(label = "备注", value = note)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = openPicker,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "改分类")
                    }
                    Button(
                        onClick = onRevoke,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "撤销这笔")
                    }
                }

                Text(
                    text = when {
                        // 补分类：重点是「这笔早就记过了」，金额没有被重复计入
                        reason == ReviewCardReason.ENRICHED ->
                            "这笔之前已经记过，刚把分类补成「$shownCategoryName」，金额没有重复计入。"

                        bill.type == BillType.TRANSFER ->
                            "已自动记好（转账不计入收支）。不处理就保持这样。"

                        else ->
                            "已自动记好。不处理就保持这样，分类不对可以改。"
                    },
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.color.onSurfaceVariant
                )
            }
        }

        // 分类选择卡片：和确认卡一样从**屏幕下缘**滑入，出现时盖在确认卡之上
        AnimatedVisibility(
            visible = pickerVisible,
            enter = fadeIn(tween(CARD_ENTER_DURATION_MS)) + slideInVertically(
                animationSpec = tween(CARD_ENTER_DURATION_MS, easing = FastOutSlowInEasing)
            ) { height -> height + bottomMarginPx },
            exit = fadeOut(tween(PICKER_EXIT_MILLIS)) + slideOutVertically(
                animationSpec = tween(PICKER_EXIT_MILLIS, easing = FastOutSlowInEasing)
            ) { height -> height + bottomMarginPx }
        ) {
            CategoryPickerPanel(
                tree = categoryTree,
                selectedId = pendingCategoryId ?: currentCategoryId,
                expandedParentId = expandedParentId,
                // 点一级：选中它 + 切换展开（与「记一笔」页同一套交互，见 RecordViewModel.onParentClick）。
                // 父级自身必须可选：判不出方向的转账会落在「19 转账」，用户得能把它选回来。
                onParentClick = { id ->
                    pendingCategoryId = id
                    expandedParentId = if (expandedParentId == id) null else id
                },
                // 点一下只是「暂选」，不动账；真正落库要等「确认」
                onSelect = { id -> pendingCategoryId = id },
                onBack = { pickerVisible = false },
                onConfirm = {
                    val id = pendingCategoryId
                    if (id != null && id != currentCategoryId) {
                        currentCategoryId = id
                        shownCategoryName = categoryTree.nameOf(id) ?: shownCategoryName
                        onSelectCategory(id)
                    }
                    // 确认后收起选择器、**回到原来那张确认卡**，而不是把整张卡片关掉
                    pickerVisible = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(CardBottomMargin)
            )
        }
    }
}

/**
 * 分类选择卡片。
 *
 * 内容直接用「记一笔」页的 [CategoryPicker]，容器用同页的 [GroupCard] + 12dp 内边距，
 * 因此网格、图标、选中态、二级展开动画与**应用内完全一致**（不是照着重画一遍）。
 *
 * 底部「返回 / 确认」沿用确认卡上那一对按钮的样式（取消在左、主操作在右）：
 * 选中分类**只是暂选**，点「确认」才落库并退回确认卡，点「返回」原样退回、什么也不改。
 *
 * `onManageCategory` 传 null：那格「＋新增」在记一笔页是跳分类管理用的，
 * 悬浮层里没有导航栈可跳，留着只会是一个点了没反应的按钮。
 */
@Composable
private fun CategoryPickerPanel(
    tree: List<CategoryNode>,
    selectedId: Long?,
    expandedParentId: Long?,
    onParentClick: (Long) -> Unit,
    onSelect: (Long) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 分类多时要能滚，但整块面板不能顶穿屏幕：按屏高比例设上限
    val maxListHeight = (LocalConfiguration.current.screenHeightDp * PICKER_MAX_SCREEN_RATIO).dp

    GroupCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "选择分类",
                style = AppTheme.typography.titleSmall,
                color = AppTheme.color.onSurface,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .heightIn(max = maxListHeight)
                    .verticalScroll(rememberScrollState())
            ) {
                CategoryPicker(
                    tree = tree,
                    selectedId = selectedId,
                    expandedParentId = expandedParentId,
                    onSelect = onSelect,
                    onParentClick = onParentClick,
                    onManageCategory = null
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "返回")
                }
                Button(
                    onClick = onConfirm,
                    // 没选到任何分类就没什么可确认的（分类树为空时按钮保持禁用）
                    enabled = selectedId != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "确认")
                }
            }
        }
    }
}

/**
 * 按 id 取分类名（一级或二级）。
 *
 * 选中后用它在本地改名，省掉一次「写库 → 回读分类名」的往返。
 */
private fun List<CategoryNode>.nameOf(categoryId: Long): String? {
    forEach { node ->
        if (node.parent.id == categoryId) return node.parent.name
        node.children.firstOrNull { it.id == categoryId }?.let { return it.name }
    }
    return null
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

/** 卡片里只显示到分钟：它回答的是「这是哪一笔」，秒级精度在这里没有意义 */
private fun formatCardTime(millis: Long): String =
    if (millis <= 0L) {
        "未知时间"
    } else {
        SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(millis))
    }

/** 卡片距屏幕左右与下缘的留白；入场位移要用到它，故提为常量 */
private val CardBottomMargin = 16.dp

/**
 * 入场时长（毫秒）。
 *
 * 取 280ms：比常见的 200ms 略长一点，让「从屏幕下缘滑上来」这段位移被看清；
 * 又不至于长到让用户觉得卡片慢半拍才出现。
 */
private const val CARD_ENTER_DURATION_MS = 280

/** 遮罩最终不透明度；随入场进度从 0 淡到此值 */
private const val SCRIM_ALPHA = 0.35f

/** 分类选择卡片收起时的时长；比入场略快，收尾更利落 */
private const val PICKER_EXIT_MILLIS = 200

/** 选择器弹起时，底下那张确认卡的压暗程度 */
private const val DIMMED_CARD_ALPHA = 0.35f

/**
 * 分类面板的可视高度上限（占屏幕高度的比例）。
 *
 * 分类多时面板会很长，直接把卡片顶到屏幕外；按屏高比例限制并允许内部滚动，
 * 既不遮住整屏，也不会在不同尺寸的机型上固定成一个不合适的绝对值。
 */
private const val PICKER_MAX_SCREEN_RATIO = 0.45f
