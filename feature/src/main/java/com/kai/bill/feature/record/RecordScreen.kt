package com.kai.bill.feature.record

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kai.bill.core.common.money.MoneyFormatter
import com.kai.bill.core.design.component.GroupCard
import com.kai.bill.core.design.component.SegmentTabs
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode
import com.kai.bill.domain.model.BillType
import com.kai.bill.feature.R
import com.kai.bill.feature.record.components.AccountSelector
import com.kai.bill.feature.record.components.AmountKeypad
import com.kai.bill.feature.record.components.CategoryPicker
import com.kai.bill.feature.common.SectionTitle
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.yield

/**
 * 二级分类在「页面转场结束后」再展开的等待时长。
 *
 * 略大于页面转场（滑动 300ms）：转场进行中就展开的话，两层动画会互相抢视觉，
 * 看起来像「页面还没停稳，分类就急着弹出来」。
 */
private const val SUB_GRID_REVEAL_DELAY_MILLIS = 320L

/**
 * 记一笔页面（新增 / 编辑复用）。
 *
 * 自上而下：顶栏 → 类型切换 → 金额区（含交易日期）→ 分类九宫格 → 账户选择 → 备注 → 数字键盘；
 * 页面底部固定「保存 / 取消」操作栏（独立于数字键盘，常驻可见）。
 *
 * @param uiState 记一笔状态
 * @param onTypeChange 类型切换
 * @param onDigit 数字键盘输入
 * @param onDelete 数字键盘删除
 * @param onCategorySelect 分类选择（二级或无子分类的一级）
 * @param onParentClick 点击一级：选中并切换展开/收起
 * @param onManageCategory 从分类面板跳分类管理（补一个子分类）
 * @param onAccountSelect 账户选择
 * @param onNoteChange 备注变更
 * @param onDateTimeSelect 选择交易日期时间（精确到分）
 * @param onSave 保存
 * @param onCancel 取消（放弃改动并返回）
 * @param onDeleteBill 删除（仅编辑态可用）
 * @param onClearError 清除错误提示
 * @param onBack 返回
 * @param modifier 外部修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    uiState: RecordUiState,
    onTypeChange: (BillType) -> Unit,
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onCategorySelect: (Long) -> Unit,
    onParentClick: (Long) -> Unit,
    onManageCategory: () -> Unit,
    onAccountSelect: (Long) -> Unit,
    onNoteChange: (String) -> Unit,
    onCountInStatsChange: (Boolean) -> Unit = {},
    onDateTimeSelect: (LocalDateTime) -> Unit = {},
    onSave: () -> Unit,
    onDeleteBill: () -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit = onBack,
    modifier: Modifier = Modifier,
    onClearError: () -> Unit = {}
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var keypadVisible by remember { mutableStateOf(false) }
    // 首帧只画顶栏+金额，分类网格/账户选择等数据到位后再挂，避免进入页时与滑动抢同一帧
    var heavyReady by remember { mutableStateOf(false) }
    val typeTabs = remember { listOf(BillType.EXPENSE, BillType.INCOME) }

    val zone = remember { ZoneId.systemDefault() }
    // 交易日期时间（年月日 + 时分），随选择的交易时间变化
    val tradeDateTime = remember(uiState.tradeTimeMillis) {
        Instant.ofEpochMilli(uiState.tradeTimeMillis).atZone(zone).toLocalDateTime()
    }
    val tradeDateText = remember(tradeDateTime) {
        "${tradeDateTime.year}年${tradeDateTime.monthValue}月${tradeDateTime.dayOfMonth}日"
    }
    val tradeTimeText = remember(tradeDateTime) {
        "%02d:%02d".format(tradeDateTime.hour, tradeDateTime.minute)
    }

    // 分类数据到位后就挂载重内容。
    // 若数据未到就挂载：categories 为空会先落到「暂无分类」提示（很矮），
    // 数据到达后又变成网格（很高），备注框被顶上顶下两次 —— 这就是「闪一下」的来源。
    // 只看分类树：账户允许为空，把它也当条件会在「无账户」时永远等不到，
    // 只能靠下面的兜底超时，白白多等一截。
    LaunchedEffect(uiState.categoryTree) {
        if (uiState.categoryTree.isNotEmpty()) {
            yield()
            heavyReady = true
        }
    }

    // 兜底：数据异常为空时也要挂载，避免永远停在占位高度。
    // 时长压短：正常情况下分类树几十毫秒就到，这段只是防呆。
    LaunchedEffect(Unit) {
        delay(200)
        heavyReady = true
    }

    // 二级分类等页面转场走完再展开：转场中同时展开两层动画会互相干扰。
    // 一级网格照常先出现（它由数据驱动），只是「展开子级」这一步往后挪。
    var revealSubGrid by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SUB_GRID_REVEAL_DELAY_MILLIS)
        revealSubGrid = true
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 顶栏
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
                Text(text = "‹", style = AppTheme.typography.headlineLarge, color = AppTheme.color.onSurface)
            }
            Text(
                text = if (uiState.isEditing) "编辑账单" else "记一笔",
                style = AppTheme.typography.titleLarge,
                color = AppTheme.color.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (uiState.isEditing) {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .clickable { showDeleteConfirm = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🗑", style = AppTheme.typography.titleMedium, color = AppTheme.ext.expense)
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "type") {
                SegmentTabs(
                    items = typeTabs,
                    selected = uiState.type,
                    onSelect = onTypeChange,
                    labelOf = { if (it == BillType.EXPENSE) "支出" else "收入" }
                )
            }
            item(key = "amount") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp)
                            .border(
                                width = 1.5.dp,
                                color = if (keypadVisible) AppTheme.color.primary else AppTheme.color.outline,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .background(
                                color = AppTheme.color.surface,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { keypadVisible = !keypadVisible },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = MoneyFormatter.withSymbol(uiState.amountCents),
                            style = AppTheme.typography.headlineLarge.copy(
                                color = AppTheme.color.primary,
                                textAlign = TextAlign.Center
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                    Text(
                        text = "本次金额",
                        textAlign = TextAlign.Center,
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.color.onSurfaceVariant
                    )
                    // 交易日期时间：日期与时分分别可点，点击或点键盘日历键都能修改
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_calendar),
                            contentDescription = null,
                            tint = AppTheme.color.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = tradeDateText,
                            style = AppTheme.typography.bodyMedium,
                            color = AppTheme.color.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showDatePicker = true }
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                        Text(
                            text = tradeTimeText,
                            style = AppTheme.typography.bodyMedium,
                            color = AppTheme.color.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showTimePicker = true }
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            item(key = "category_title") {
                SectionTitle(text = "分类")
            }
            item(key = "category_grid") {
                // 这里**不做**高度动画：二级分类展开自带 expandVertically，
                // 若此处再叠一层 animateContentSize，卡片高度会「追着」子网格动画跑，
                // 表现为卡片与下方内容跟二级展开不同步。高度只保留一个动画源。
                Box(modifier = Modifier.fillMaxWidth()) {
                    when {
                        !heavyReady -> Box(modifier = Modifier.fillMaxWidth().height(180.dp))
                        uiState.categoryTree.isEmpty() -> HintText(text = "暂无分类，请稍候或到设置里添加")
                        else -> GroupCard {
                            Box(modifier = Modifier.padding(12.dp)) {
                                CategoryPicker(
                                    tree = uiState.categoryTree,
                                    selectedId = uiState.categoryId,
                                    // 转场结束前不展开子级，避免与页面转场动画叠加
                                    expandedParentId = if (revealSubGrid) {
                                        uiState.expandedParentId
                                    } else {
                                        null
                                    },
                                    onSelect = onCategorySelect,
                                    onParentClick = onParentClick,
                                    onManageCategory = onManageCategory
                                )
                            }
                        }
                    }
                }
            }
            item(key = "account_title") {
                SectionTitle(text = "账户")
            }
            item(key = "account_selector") {
                when {
                    !heavyReady -> Box(modifier = Modifier.fillMaxWidth().height(40.dp))
                    uiState.accounts.isEmpty() -> HintText(text = "暂无账户，可不指定账户直接保存")
                    else -> GroupCard {
                        Box(modifier = Modifier.padding(12.dp)) {
                            AccountSelector(
                                accounts = uiState.accounts,
                                selectedId = uiState.accountId,
                                onSelect = onAccountSelect
                            )
                        }
                    }
                }
            }
            item(key = "note") {
                GroupCard {
                    Box(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = uiState.note,
                            onValueChange = onNoteChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("备注（可选）") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                        )
                    }
                }
            }
            item(key = "count_in_stats") {
                GroupCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "计入统计与预算",
                                style = AppTheme.typography.bodyLarge,
                                color = AppTheme.color.onSurface
                            )
                            Text(
                                text = "关闭后这笔不计入本月支出、预算与图表",
                                style = AppTheme.typography.bodySmall,
                                color = AppTheme.color.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.countInStats,
                            onCheckedChange = onCountInStatsChange
                        )
                    }
                }
            }
            uiState.errorMessage?.let { message ->
                item(key = "error") {
                    Text(
                        text = message,
                        color = AppTheme.ext.expense,
                        style = AppTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onClearError() }
                    )
                }
            }
        }

        // 数字键盘：在底部操作栏上方滑入 / 滑出
        // 注：不使用带 elevation 的 Surface，避免滑动动画时阴影逐帧重算造成卡顿；
        // 同时使用 expand/shrink 让键盘高度与页面布局同步变化，避免 slide 时留下覆盖区域。
        AnimatedVisibility(
            visible = keypadVisible,
            enter = slideInVertically(
                animationSpec = tween(250, easing = FastOutSlowInEasing),
                initialOffsetY = { it }
            ) + expandVertically(
                animationSpec = tween(250, easing = FastOutSlowInEasing),
                expandFrom = Alignment.Bottom
            ),
            exit = slideOutVertically(
                animationSpec = tween(200, easing = FastOutSlowInEasing),
                targetOffsetY = { it }
            ) + shrinkVertically(
                animationSpec = tween(200, easing = FastOutSlowInEasing),
                shrinkTowards = Alignment.Bottom
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppTheme.color.surface)
            ) {
                AmountKeypad(
                    onDigit = onDigit,
                    onDelete = onDelete,
                    onPickDate = { showDatePicker = true },
                    onCollapse = { keypadVisible = false }
                )
            }
        }

        // 底部操作栏：取消 / 保存，常驻页面底部（即其"原位置"），键盘在其上方滑入滑出；
        // 收起时操作栏保持原位、不随键盘一起消失
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = AppTheme.color.surface,
            shadowElevation = 8.dp,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 取消：放弃改动并返回
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppTheme.color.outline.copy(alpha = 0.16f))
                        .clickable(onClick = onCancel),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (uiState.isEditing) "取消修改" else "取消",
                        style = AppTheme.typography.titleMedium,
                        color = AppTheme.color.onSurface
                    )
                }
                // 保存
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (uiState.canSave) AppTheme.color.primary
                            else AppTheme.color.primary.copy(alpha = 0.38f)
                        )
                        .clickable(enabled = uiState.canSave, onClick = onSave),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (uiState.isEditing) "保存修改" else "保存",
                        style = AppTheme.typography.titleMedium,
                        color = if (uiState.canSave) AppTheme.color.onPrimary
                        else AppTheme.color.onPrimary.copy(alpha = 0.60f)
                    )
                }
            }
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text(text = "删除这笔账单？") },
                text = { Text(text = "删除后无法恢复，确定要删除吗？") },
                confirmButton = {
                    Text(
                        text = "删除",
                        color = AppTheme.ext.expense,
                        modifier = Modifier
                            .clickable {
                                showDeleteConfirm = false
                                onDeleteBill()
                            }
                            .padding(12.dp)
                    )
                },
                dismissButton = {
                    Text(
                        text = "取消",
                        color = AppTheme.color.onSurfaceVariant,
                        modifier = Modifier
                            .clickable { showDeleteConfirm = false }
                            .padding(12.dp)
                    )
                }
            )
        }

        // 日期选择：Material3 DatePicker 支持年 / 月 / 日三级编辑，并可切换键盘输入。
        // 其内部以 UTC 毫秒表示日期，故进入与回写都要按 UTC 换算，避免差一天。
        if (showDatePicker) {
            val pickerState = rememberDatePickerState(
                initialSelectedDateMillis = Instant.ofEpochMilli(uiState.tradeTimeMillis)
                    .atZone(zone).toLocalDate()
                    .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    Text(
                        text = "确定",
                        color = AppTheme.color.primary,
                        modifier = Modifier
                            .clickable {
                                pickerState.selectedDateMillis?.let { utcMillis ->
                                    val pickedDate = Instant.ofEpochMilli(utcMillis)
                                        .atZone(ZoneOffset.UTC).toLocalDate()
                                    // 只改日期，保留原时分
                                    onDateTimeSelect(
                                        pickedDate.atTime(tradeDateTime.hour, tradeDateTime.minute)
                                    )
                                }
                                showDatePicker = false
                            }
                            .padding(12.dp)
                    )
                },
                dismissButton = {
                    Text(
                        text = "取消",
                        color = AppTheme.color.onSurfaceVariant,
                        modifier = Modifier
                            .clickable { showDatePicker = false }
                            .padding(12.dp)
                    )
                }
            ) {
                DatePicker(state = pickerState)
            }
        }

        // 时间选择：精确到分（24 小时制），与日期共用同一天的交易时间
        if (showTimePicker) {
            val timeState = rememberTimePickerState(
                initialHour = tradeDateTime.hour,
                initialMinute = tradeDateTime.minute,
                is24Hour = true
            )
            AlertDialog(
                onDismissRequest = { showTimePicker = false },
                title = { Text(text = "选择时间") },
                text = { TimePicker(state = timeState) },
                confirmButton = {
                    Text(
                        text = "确定",
                        color = AppTheme.color.primary,
                        modifier = Modifier
                            .clickable {
                                onDateTimeSelect(
                                    tradeDateTime.toLocalDate()
                                        .atTime(timeState.hour, timeState.minute)
                                )
                                showTimePicker = false
                            }
                            .padding(12.dp)
                    )
                },
                dismissButton = {
                    Text(
                        text = "取消",
                        color = AppTheme.color.onSurfaceVariant,
                        modifier = Modifier
                            .clickable { showTimePicker = false }
                            .padding(12.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = AppTheme.typography.bodyMedium,
        color = AppTheme.color.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    )
}

/**
 * 记一笔的路由，负责接上 ViewModel 与导航回调。
 *
 * @param billId 编辑目标账单 ID；0 表示新增
 * @param onClose 保存/删除成功后关闭页面
 * @param onManageCategory 跳分类管理（补一个子分类）
 * @param modifier 外部修饰符
 * @param viewModel 由 Hilt 注入
 */
@Composable
fun RecordRoute(
    onClose: () -> Unit,
    onManageCategory: () -> Unit,
    modifier: Modifier = Modifier,
    billId: Long = 0L,
    viewModel: RecordViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                RecordEvent.Close -> onClose()
            }
        }
    }
    LaunchedEffect(billId) {
        if (billId != 0L) viewModel.load(billId)
    }
    RecordScreen(
        uiState = uiState,
        onTypeChange = viewModel::onTypeChange,
        onDigit = viewModel::onDigit,
        onDelete = viewModel::onDelete,
        onCategorySelect = viewModel::onCategorySelect,
        onParentClick = viewModel::onParentClick,
        onManageCategory = onManageCategory,
        onAccountSelect = viewModel::onAccountSelect,
        onNoteChange = viewModel::onNoteChange,
        onCountInStatsChange = viewModel::onCountInStatsChange,
        onDateTimeSelect = viewModel::onDateTimeSelected,
        onSave = viewModel::save,
        onDeleteBill = viewModel::delete,
        onClearError = viewModel::clearError,
        onBack = onClose,
        modifier = modifier,
    )
}

@Preview(name = "记一笔-浅色", showBackground = true)
@Composable
private fun RecordScreenLightPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.LIGHT) {
        RecordScreen(
            uiState = RecordUiState(amountCents = 123450),
            onTypeChange = {},
            onDigit = {},
            onDelete = {},
            onCategorySelect = {},
            onParentClick = {},
            onManageCategory = {},
            onAccountSelect = {},
            onNoteChange = {},
            onDateTimeSelect = {},
            onSave = {},
            onDeleteBill = {},
            onBack = {}
        )
    }
}
