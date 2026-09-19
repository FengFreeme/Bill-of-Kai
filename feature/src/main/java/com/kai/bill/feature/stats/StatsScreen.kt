package com.kai.bill.feature.stats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kai.bill.core.common.money.MoneyFormatter
import com.kai.bill.core.common.percent.PercentFormatter
import com.kai.bill.core.design.component.EmptyState
import com.kai.bill.core.design.component.GroupCard
import com.kai.bill.core.design.component.SegmentTabs
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode
import com.kai.bill.domain.model.Account
import com.kai.bill.domain.model.BillFilter
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.Category
import com.kai.bill.domain.model.stats.CategoryStatsMode
import com.kai.bill.feature.common.DatePickerSheet
import com.kai.bill.feature.common.DateRangeKind
import com.kai.bill.feature.common.DateRangeSwitcher
import com.kai.bill.feature.common.SectionTitle
import com.kai.bill.feature.common.rangeLabel
import com.kai.bill.feature.stats.components.AccountStatsSection
import com.kai.bill.feature.stats.components.CategoryRankList
import com.kai.bill.feature.stats.components.DonutChart
import com.kai.bill.feature.stats.components.DonutSlice
import com.kai.bill.feature.stats.components.StatsFilterPanel
import com.kai.bill.feature.stats.components.StatsOverviewPanel
import com.kai.bill.feature.stats.components.TrendLineChart
import com.kai.bill.feature.stats.components.WeeklyBarChart
import com.kai.bill.feature.stats.components.gradientSliceColors
import kotlin.math.roundToInt

/**
 * 统计页：粒度切换 + 筛选入口 → 类型切换 → 概览 → 分类构成 → 排行 → 账户维度 → 趋势。
 *
 * M2：把 M0 的 [EmptyState] 占位换成真实图表（Vico）与完整筛选器。
 *
 * **筛选直接作用于统计**：概览 / 分类构成 / 排行 / 趋势都按筛选结果重算，
 * 所以不再单独列「筛选结果」明细 —— 图表本身就是筛选结果，
 * 再挂一份流水清单会让统计页本末倒置。
 *
 * @param uiState 统计页状态
 * @param categories 全部分类（筛选面板候选项 + 流水列表的名称映射）
 * @param accounts 全部账户（同上）
 * @param draft 筛选面板草稿
 * @param showFilterPanel 是否展开筛选面板
 * @param onRangeKindSelected 时间粒度切换回调
 * @param onDateAnchorSelected 在日 / 周 / 月 / 年弹层里选定具体日期回调（毫秒）
 * @param onStatTypeSelected 统计维度（支出 / 收入 / 转账）切换回调
 * @param onCategoryStatsModeSelected 分类统计维度（主分类 / 子分类）切换回调
 * @param onCategoryClick 点击分类排行项：进入该分类的详情页
 * @param modifier 外部修饰符
 */
@Composable
fun StatsScreen(
    uiState: StatsUiState,
    categories: List<Category>,
    accounts: List<Account>,
    draft: StatsFilterDraft,
    showFilterPanel: Boolean,
    onRangeKindSelected: (DateRangeKind) -> Unit,
    onDateAnchorSelected: (Long) -> Unit,
    onStatTypeSelected: (BillType) -> Unit,
    onCategoryStatsModeSelected: (CategoryStatsMode) -> Unit,
    onCategoryClick: (Long) -> Unit,
    onFilterClick: () -> Unit,
    onDismissFilterPanel: () -> Unit,
    onDraftTypeSelected: (BillType?) -> Unit,
    onDraftCategoryToggled: (Long) -> Unit,
    onDraftAccountToggled: (Long) -> Unit,
    onDraftUnspecifiedAccountToggled: () -> Unit,
    onDraftAllCategoriesSelected: () -> Unit,
    onDraftAllAccountsSelected: () -> Unit,
    onDraftAllCategoriesCleared: () -> Unit,
    onDraftAllAccountsCleared: () -> Unit,
    onDraftReset: () -> Unit,
    onApplyFilter: () -> Unit,
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var pickerKind by remember(uiState.rangeKind) { mutableStateOf(uiState.rangeKind) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "switcher") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DateRangeSwitcher(
                            selected = uiState.rangeKind,
                            onSelect = onRangeKindSelected,
                            modifier = Modifier.weight(1f)
                        )
                        // 筛选入口：生效时高亮，让用户一眼看出「当前是筛选后的数据」
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (uiState.isFiltering) {
                                        AppTheme.color.primary.copy(alpha = 0.14f)
                                    } else {
                                        AppTheme.color.surfaceVariant
                                    }
                                )
                                .clickable(onClick = onFilterClick)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "筛选",
                                style = AppTheme.typography.labelLarge,
                                color = if (uiState.isFiltering) {
                                    AppTheme.color.primary
                                } else {
                                    AppTheme.color.onSurfaceVariant
                                }
                            )
                        }
                    }
                    // 当前区间的可点选标签：点开后弹日 / 周 / 月 / 年取值面板
                    // 宽度贴合文字即可 —— 撑满整行会让短标签后面空出一大片
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppTheme.color.surfaceVariant)
                            .clickable {
                                pickerKind = uiState.rangeKind
                                showDatePicker = true
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "${rangeLabel(uiState.rangeKind, uiState.selectedDate)}  ▾",
                            style = AppTheme.typography.labelLarge,
                            color = AppTheme.color.onSurface
                        )
                    }
                }
            }

            if (uiState.isFiltering) {
                item(key = "filter_summary") {
                    FilterSummaryBar(
                        chips = remember(uiState.filter, categories, accounts) {
                            filterChips(uiState.filter, categories, accounts)
                        },
                        count = uiState.bills.size,
                        onClear = onClearFilter,
                        onEdit = onFilterClick
                    )
                }
            }

            item(key = "type_tabs") {
                SegmentTabs(
                    items = listOf(BillType.EXPENSE, BillType.INCOME, BillType.TRANSFER),
                    selected = uiState.statType,
                    onSelect = onStatTypeSelected,
                    labelOf = {
                        when (it) {
                            BillType.EXPENSE -> "支出"
                            BillType.INCOME -> "收入"
                            BillType.TRANSFER -> "转账"
                        }
                    }
                )
            }

            // 概览面板是「支出 / 收入」口径（转账本就不计入收支），转账维度下没有意义，
            // 转账总额由下面分类构成圈的圆心承担。
            if (uiState.statType != BillType.TRANSFER) {
                item(key = "overview") {
                    StatsOverviewPanel(overview = uiState.overview)
                }
            }

            item(key = "category_header") {
                CategoryStatsHeader(
                    mode = uiState.categoryStatsMode,
                    onModeSelected = onCategoryStatsModeSelected
                )
            }
            item(key = "category_chart") {
                if (uiState.categoryStats.isEmpty()) {
                    EmptyState(
                        title = "暂无分类数据",
                        subtitle = "记几笔账之后，这里会显示各类别的占比"
                    )
                } else {
                    var selectedIndex by remember(uiState.categoryStats) { mutableIntStateOf(-1) }
                    val slices = remember(uiState.categoryStats) {
                        val colors = gradientSliceColors(uiState.categoryStats.size)
                        uiState.categoryStats.mapIndexed { index, stat ->
                            DonutSlice(
                                name = stat.categoryName,
                                ratio = stat.ratio,
                                color = colors.getOrElse(index) { Color(0xFF5B8DEF) }
                            )
                        }
                    }
                    val baseCaption = when (uiState.statType) {
                        BillType.EXPENSE -> "共支出(元)"
                        BillType.INCOME -> "共收入(元)"
                        BillType.TRANSFER -> "共转账(元)"
                    }
                    val baseTotalCents = when (uiState.statType) {
                        BillType.EXPENSE -> uiState.overview.expenseCents
                        BillType.INCOME -> uiState.overview.incomeCents
                        // 转账不进概览（overview 只有收支两项），圆心总额取分类构成之和
                        BillType.TRANSFER -> uiState.categoryStats.sumOf { it.amountCents }
                    }
                    val centerTitle: String
                    val centerCaption: String
                    if (selectedIndex in uiState.categoryStats.indices) {
                        val stat = uiState.categoryStats[selectedIndex]
                        centerTitle = MoneyFormatter.plain(stat.amountCents)
                        centerCaption = "${stat.categoryName} ${PercentFormatter.of(stat.ratio)}"
                    } else {
                        centerTitle = MoneyFormatter.plain(baseTotalCents)
                        centerCaption = baseCaption
                    }
                    GroupCard {
                        // 水平只留 4dp：标签文字本身还有 labelPad 内边距，加起来才不至于离卡片边太远
                        Box(modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
                            DonutChart(
                                slices = slices,
                                centerTitle = centerTitle,
                                centerCaption = centerCaption,
                                selectedIndex = selectedIndex,
                                onSliceClick = { selectedIndex = if (selectedIndex == it) -1 else it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp)
                            )
                        }
                    }
                }
            }
            item(key = "category_rank") {
                if (uiState.categoryStats.isNotEmpty()) {
                    GroupCard {
                        Box(modifier = Modifier.padding(16.dp)) {
                            CategoryRankList(
                                stats = uiState.categoryStats,
                                onItemClick = onCategoryClick
                            )
                        }
                    }
                }
            }

            item(key = "account_title") {
                SectionTitle(text = "账户维度")
            }
            item(key = "account_stats") {
                if (uiState.accountStats.isEmpty()) {
                    EmptyState(
                        title = "暂无账户数据",
                        subtitle = "记几笔账之后，这里会显示各账户的占比"
                    )
                } else {
                    AccountStatsSection(stats = uiState.accountStats)
                }
            }

            item(key = "trend_title") {
                SectionTitle(
                    text = when (uiState.statType) {
                        BillType.EXPENSE -> "支出趋势"
                        BillType.INCOME -> "收入趋势"
                        BillType.TRANSFER -> "转账趋势"
                    }
                )
            }
            item(key = "trend_chart") {
                if (uiState.trendPoints.isEmpty()) {
                    EmptyState(
                        title = "暂无趋势数据",
                        subtitle = "这段时间还没有记账"
                    )
                } else {
                    GroupCard {
                        Box(modifier = Modifier.padding(12.dp)) {
                            TrendLineChart(
                                points = uiState.trendPoints,
                                monthly = uiState.rangeKind == DateRangeKind.YEAR,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            )
                        }
                    }
                }
            }

            if (uiState.rangeKind != DateRangeKind.YEAR) {
                item(key = "week_bar_title") {
                    SectionTitle(
                        text = when (uiState.statType) {
                            BillType.EXPENSE -> "周支出对比"
                            BillType.INCOME -> "周收入对比"
                            BillType.TRANSFER -> "周转账对比"
                        }
                    )
                }
                item(key = "week_bar_chart") {
                    if (uiState.weeklyBars.isEmpty()) {
                        EmptyState(
                            title = "暂无对比数据",
                            subtitle = "记几笔账之后，这里会显示近 5 周的对比"
                        )
                    } else {
                        GroupCard {
                            Box(modifier = Modifier.padding(12.dp)) {
                                WeeklyBarChart(
                                    bars = uiState.weeklyBars,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                )
                            }
                        }
                    }
                }
            }

        }

        AnimatedVisibility(
            visible = showFilterPanel,
            enter = slideInVertically(
                animationSpec = tween(280, easing = FastOutSlowInEasing),
                initialOffsetY = { it }
            ),
            exit = slideOutVertically(
                animationSpec = tween(240, easing = FastOutSlowInEasing),
                targetOffsetY = { it }
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    // 点遮罩关闭；面板自身区域吃掉点击，避免穿透到遮罩
                    .clickable(onClick = onDismissFilterPanel),
                contentAlignment = Alignment.BottomCenter
            ) {
                StatsFilterPanel(
                    categories = categories,
                    accounts = accounts,
                    draft = draft,
                    onTypeSelected = onDraftTypeSelected,
                    onCategoryToggled = onDraftCategoryToggled,
                    onAccountToggled = onDraftAccountToggled,
                    onUnspecifiedAccountToggled = onDraftUnspecifiedAccountToggled,
                    onSelectAllCategories = onDraftAllCategoriesSelected,
                    onSelectAllAccounts = onDraftAllAccountsSelected,
                    onClearAllCategories = onDraftAllCategoriesCleared,
                    onClearAllAccounts = onDraftAllAccountsCleared,
                    onReset = onDraftReset,
                    onApply = onApplyFilter,
                    onDismiss = onDismissFilterPanel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = false) { }
                )
            }
        }

        AnimatedVisibility(
            visible = showDatePicker,
            enter = slideInVertically(
                animationSpec = tween(280, easing = FastOutSlowInEasing),
                initialOffsetY = { it }
            ),
            exit = slideOutVertically(
                animationSpec = tween(240, easing = FastOutSlowInEasing),
                targetOffsetY = { it }
            )
        ) {
            DatePickerSheet(
                kind = pickerKind,
                selectedDate = uiState.selectedDate,
                onSelect = { millis ->
                    onDateAnchorSelected(millis)
                    showDatePicker = false
                },
                onDismiss = { showDatePicker = false }
            )
        }
    }
}

/**
 * 分类构成区头部：标题 + 「主分类 / 子分类」维度切换。
 *
 * 默认主分类（一级上卷），切到子分类后饼图与排行按二级明细重算，
 * 与分类详情页的环形图口径一致。
 */
@Composable
private fun CategoryStatsHeader(
    mode: CategoryStatsMode,
    onModeSelected: (CategoryStatsMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(text = "分类构成")
        SegmentTabs(
            items = listOf(CategoryStatsMode.PARENT, CategoryStatsMode.CHILD),
            selected = mode,
            onSelect = onModeSelected,
            labelOf = { if (it == CategoryStatsMode.PARENT) "主分类" else "子分类" }
        )
    }
}

/**
 * 已选条件摘要条：把当前筛选翻译成人话，并提供一键清除。
 *
 * 只有 Chip 文案而没有「共 N 笔」时，用户无法确认筛选是否真的生效，
 * 所以把结果条数一起带上。
 */
@Composable
private fun FilterSummaryBar(
    chips: List<String>,
    count: Int,
    onClear: () -> Unit,
    onEdit: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onEdit),
        color = AppTheme.color.surfaceVariant,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = buildString {
                    append(chips.joinToString("、").ifEmpty { "自定义条件" })
                    append(" · 共 $count 笔")
                },
                style = AppTheme.typography.bodySmall,
                color = AppTheme.color.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "清除",
                style = AppTheme.typography.labelLarge,
                color = AppTheme.color.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClear)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}

/** 把 [BillFilter] 翻译成可读的条件文案 */
private fun filterChips(
    filter: BillFilter?,
    categories: List<Category>,
    accounts: List<Account>
): List<String> {
    if (filter == null) return emptyList()
    val chips = mutableListOf<String>()
    filter.type?.let {
        chips += if (it == BillType.EXPENSE) "支出" else "收入"
    }
    // 筛选结果中只展示一级分类名称（二级 id 是内部展开，不应出现在摘要里）
    chips += categories.filter { it.parentId == null && it.id in filter.categoryIds }.map { it.name }
    chips += accounts.filter { it.id in filter.accountIds }.map { it.name }
    if (filter.includeUnspecifiedAccount) chips += "未指定账户"
    return chips
}

/**
 * 统计页路由，负责接上 ViewModel。
 *
 * @param onCategoryClick 点击分类排行项：进入分类详情
 * @param modifier 外部修饰符
 * @param viewModel 由 Hilt 注入
 */
@Composable
fun StatsRoute(
    onCategoryClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val draft by viewModel.filterDraft.collectAsStateWithLifecycle()
    val panelOpen by viewModel.filterPanelOpen.collectAsStateWithLifecycle()

    StatsScreen(
        uiState = uiState,
        categories = categories,
        accounts = accounts,
        draft = draft,
        showFilterPanel = panelOpen,
        onRangeKindSelected = viewModel::onRangeKindSelected,
        onDateAnchorSelected = viewModel::onDateAnchorSelected,
        onStatTypeSelected = viewModel::onStatTypeSelected,
        onCategoryStatsModeSelected = viewModel::onCategoryStatsModeSelected,
        onCategoryClick = onCategoryClick,
        onFilterClick = viewModel::openFilterPanel,
        onDismissFilterPanel = viewModel::closeFilterPanel,
        onDraftTypeSelected = viewModel::onDraftTypeSelected,
        onDraftCategoryToggled = viewModel::onDraftCategoryToggled,
        onDraftAccountToggled = viewModel::onDraftAccountToggled,
        onDraftUnspecifiedAccountToggled = viewModel::onDraftUnspecifiedAccountToggled,
        onDraftAllCategoriesSelected = viewModel::onDraftAllCategoriesSelected,
        onDraftAllAccountsSelected = viewModel::onDraftAllAccountsSelected,
        onDraftAllCategoriesCleared = viewModel::onDraftAllCategoriesCleared,
        onDraftAllAccountsCleared = viewModel::onDraftAllAccountsCleared,
        onDraftReset = viewModel::onDraftReset,
        onApplyFilter = viewModel::applyFilterDraft,
        onClearFilter = viewModel::onFilterCleared,
        modifier = modifier
    )
}

@Preview(name = "统计页-浅色", showBackground = true)
@Composable
private fun StatsScreenLightPreview() {
    BillOfKaiTheme(palette = AppPalette.SKY, darkMode = DarkMode.LIGHT) {
        StatsScreen(
            uiState = StatsUiState(),
            categories = emptyList(),
            accounts = emptyList(),
            draft = StatsFilterDraft(),
            showFilterPanel = false,
            onRangeKindSelected = {},
            onDateAnchorSelected = {},
            onStatTypeSelected = {},
            onCategoryStatsModeSelected = {},
            onCategoryClick = {},
            onFilterClick = {},
            onDismissFilterPanel = {},
            onDraftTypeSelected = {},
            onDraftCategoryToggled = {},
            onDraftAccountToggled = {},
            onDraftUnspecifiedAccountToggled = {},
            onDraftAllCategoriesSelected = {},
            onDraftAllAccountsSelected = {},
            onDraftAllCategoriesCleared = {},
            onDraftAllAccountsCleared = {},
            onDraftReset = {},
            onApplyFilter = {},
            onClearFilter = {}
        )
    }
}
