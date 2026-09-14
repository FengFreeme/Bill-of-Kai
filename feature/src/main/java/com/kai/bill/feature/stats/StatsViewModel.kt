package com.kai.bill.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kai.bill.core.common.time.TimeRange
import com.kai.bill.domain.model.Account
import com.kai.bill.domain.model.Bill
import com.kai.bill.domain.model.BillFilter
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.Category
import com.kai.bill.domain.model.DateRange
import com.kai.bill.domain.model.categoryIdsWithChildren
import com.kai.bill.domain.model.stats.CategoryStatsMode
import com.kai.bill.domain.model.stats.StatsGranularity
import com.kai.bill.domain.repository.AccountRepository
import com.kai.bill.domain.repository.CategoryRepository
import com.kai.bill.domain.time.Clock
import com.kai.bill.domain.usecase.bill.ObserveBillsUseCase
import com.kai.bill.domain.usecase.stats.ObserveAccountStatsUseCase
import com.kai.bill.domain.usecase.stats.ObserveCategoryStatsUseCase
import com.kai.bill.domain.usecase.stats.ObserveOverviewUseCase
import com.kai.bill.domain.usecase.stats.ObserveTrendUseCase
import com.kai.bill.feature.common.DateRangeKind
import com.kai.bill.feature.common.weekLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * 统计页 ViewModel。
 *
 * M2：把 M0 的 `MutableStateFlow` 占位换成随「粒度 + 类型 + 筛选」变化的响应式流。
 *
 * **筛选同时作用于统计与流水**：概览 / 分类构成 / 排行 / 趋势都按筛选结果重算，
 * 即「分类构成就是筛选结果」，两者口径必须一致。
 * 代价是改筛选会连带重跑四个统计查询 —— 因此无筛选时仍走 `StatsDao` 的 SQL 聚合，
 * 只有带筛选时才退化为明细的内存聚合（[StatsAggregator]），单区间千级流水完全够用。
 *
 * 分类 / 账户候选列表（[categories] / [accounts]）走独立流，不并入 [uiState]：
 * `combine` 的 typed overload 最多 5 个源，硬塞进去会逼着改用类型不安全的写法。
 *
 * 区间一律按**用户时区**计算（[TimeRange] 内部处理），
 * 用 UTC 会让东八区在早上 8 点前把账记到前一天。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val observeOverview: ObserveOverviewUseCase,
    private val observeCategoryStats: ObserveCategoryStatsUseCase,
    private val observeTrend: ObserveTrendUseCase,
    private val observeAccountStats: ObserveAccountStatsUseCase,
    private val observeBills: ObserveBillsUseCase,
    categoryRepository: CategoryRepository,
    accountRepository: AccountRepository,
    private val clock: Clock
) : ViewModel() {

    private val zone = ZoneId.systemDefault()

    private val rangeKind = MutableStateFlow(DateRangeKind.MONTH)
    /** 当前粒度下的锚定日期（毫秒），周 / 月 / 年切换器在此基础上平移 */
    private val selectedDate = MutableStateFlow(clock.nowMillis())
    private val statType = MutableStateFlow(BillType.EXPENSE)
    private val filter = MutableStateFlow<BillFilter?>(null)
    private val categoryStatsMode = MutableStateFlow(CategoryStatsMode.PARENT)

    private val _filterDraft = MutableStateFlow(StatsFilterDraft())
    private val _filterPanelOpen = MutableStateFlow(false)

    /** 筛选面板草稿；点「查看结果」才会写进 [filter] */
    val filterDraft: StateFlow<StatsFilterDraft> = _filterDraft.asStateFlow()

    /** 筛选面板是否展开 */
    val filterPanelOpen: StateFlow<Boolean> = _filterPanelOpen.asStateFlow()

    /** 筛选面板的候选项：用全量而非仅活跃，归档分类仍有历史账单在引用 */
    val categories: StateFlow<List<Category>> = categoryRepository.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val accounts: StateFlow<List<Account>> = accountRepository.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** 统计与流水的共同查询条件：粒度 + 锚定日期 + 类型 + 筛选 + 分类聚合维度 */
    private val query: Flow<StatsQuery> =
        combine(rangeKind, selectedDate, statType, filter, categoryStatsMode) { kind, date, type, currentFilter, mode ->
            StatsQuery(
                range = rangeOf(kind, date),
                granularity = granularityOf(kind),
                type = type,
                filter = currentFilter,
                mode = mode
            )
        }.distinctUntilChanged()

    /**
     * 周支出对比所需流水：以锚定周为末桶，向前取 5 个自然周。
     *
     * 单独成流，因为主区间的 [bills] 只覆盖当前粒度（月报就只有那个月），
     * 不足以撑起「近 5 周对比」——上一周若落在相邻月，主区间流水里根本没有，
     * 柱子会错误地画成 0。这里按锚定周算一个 5 周窗口再查流水。
     */
    private val weeklyBills: Flow<List<Bill>> =
        combine(selectedDate, filter) { date, currentFilter ->
            val monday = mondayOf(date)
            val startMs = monday.minusWeeks(4).atStartOfDay(zone).toInstant().toEpochMilli()
            val endMs = monday.plusDays(6)
                .atTime(23, 59, 59, 999_999_999)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
            observeBills(DateRange(startMillis = startMs, endMillis = endMs), currentFilter)
        }.flatMapLatest { it }

    val uiState: StateFlow<StatsUiState> =
        combine(
            query.flatMapLatest { q -> observeOverview(q.range, q.filter) },
            query.flatMapLatest { q -> observeCategoryStats(q.range, q.type, q.filter, q.mode) },
            query.flatMapLatest { q -> observeTrend(q.range, q.type, q.granularity, q.filter) },
            query.flatMapLatest { q -> observeAccountStats(q.range, q.type, q.filter) },
            query.flatMapLatest { q -> observeBills(q.range, q.filter) }
        ) { overview, categoryStats, trendPoints, accountStats, bills ->
            StatsUiState(
                rangeKind = rangeKind.value,
                selectedDate = selectedDate.value,
                statType = statType.value,
                categoryStatsMode = categoryStatsMode.value,
                overview = overview,
                categoryStats = categoryStats,
                trendPoints = trendPoints,
                accountStats = accountStats,
                filter = filter.value,
                bills = bills
            )
        }
            // 周对比单独再合一路流水（覆盖 5 周窗口），避免月报漏掉相邻月的上一周
            .combine(weeklyBills) { state, wBills ->
                state.copy(
                    weeklyBars = weeklyBars(wBills, state.statType, state.selectedDate)
                )
            }
        // 聚合与筛选在后台线程，避免切换粒度时拖慢主线程导致转场掉帧
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsUiState()
        )

    /** 切换时间粒度（日 / 周 / 月 / 年）；切回时锚定到当前日期 */
    fun onRangeKindSelected(kind: DateRangeKind) {
        rangeKind.value = kind
        selectedDate.value = clock.nowMillis()
    }

    /** 在维度底部弹层中选定某个具体日期（日 / 周 / 月 / 年） */
    fun onDateAnchorSelected(dateMillis: Long) {
        selectedDate.value = dateMillis
    }

    /** 切换分类统计维度（主分类 / 子分类）。 */
    fun onCategoryStatsModeSelected(mode: CategoryStatsMode) {
        categoryStatsMode.value = mode
    }

    /**
     * 切换统计维度（支出 / 收入）。
     *
     * 切换后自动重设为「该类型下全部分类 + 全部账户 + 未指定账户」，
     * 即等价不筛选，但面板里仍然显示全选。
     */
    fun onStatTypeSelected(type: BillType) {
        statType.value = type
        filter.value = null
        _filterDraft.value = defaultDraft(type)
    }

    /**
     * 打开筛选面板。
     *
     * 默认草稿为「当前类型下全部一级分类 + 全部账户 + 未指定账户」。
     * 若已有生效的筛选，则把展开后的二级 id 还原成一级 id 回填，
     * 保证 UI 只显示一级分类的勾选状态。
     */
    fun openFilterPanel() {
        _filterDraft.value = filter.value?.let { current ->
            StatsFilterDraft(
                type = current.type ?: statType.value,
                categoryIds = current.categoryIds
                    .filter { id -> categories.value.any { it.parentId == null && it.id == id } }
                    .toSet(),
                accountIds = current.accountIds,
                includeUnspecifiedAccount = current.includeUnspecifiedAccount
            )
        } ?: defaultDraft(statType.value)
        _filterPanelOpen.value = true
    }

    fun closeFilterPanel() {
        _filterPanelOpen.value = false
    }

    /**
     * 面板内切换类型：同步切换统计页类型，并自动全选新类型下的一级分类。
     */
    fun onDraftTypeSelected(type: BillType?) {
        val effective = type ?: statType.value
        statType.value = effective
        _filterDraft.value = defaultDraft(effective)
    }

    fun onDraftCategoryToggled(id: Long) {
        _filterDraft.update { draft ->
            draft.copy(
                categoryIds = if (id in draft.categoryIds) {
                    draft.categoryIds - id
                } else {
                    draft.categoryIds + id
                }
            )
        }
    }

    fun onDraftAccountToggled(id: Long) {
        _filterDraft.update { draft ->
            draft.copy(
                accountIds = if (id in draft.accountIds) {
                    draft.accountIds - id
                } else {
                    draft.accountIds + id
                }
            )
        }
    }

    fun onDraftUnspecifiedAccountToggled() {
        _filterDraft.update { it.copy(includeUnspecifiedAccount = !it.includeUnspecifiedAccount) }
    }

    fun onDraftReset() {
        _filterDraft.value = defaultDraft(statType.value)
    }

    /** 应用草稿并关闭面板 */
    fun applyFilterDraft() {
        val draft = _filterDraft.value
        // UI 只选一级，实际查询需要把二级也包含进来
        val expandedCategoryIds = draft.categoryIds
            .flatMap { rootId -> categories.value.categoryIdsWithChildren(rootId) }
            .toSet()
        val allTopLevelIds = categories.value
            .filter { it.parentId == null && it.type == draft.type }
            .map { it.id }
            .toSet()
        val allCategoryIds = allTopLevelIds
            .flatMap { rootId -> categories.value.categoryIdsWithChildren(rootId) }
            .toSet()
        val allAccountIds = accounts.value.map { it.id }.toSet()

        // 全选等价不筛选：走 StatsDao SQL 聚合，更高效
        filter.value = if (
            expandedCategoryIds == allCategoryIds &&
            draft.accountIds == allAccountIds &&
            draft.includeUnspecifiedAccount
        ) {
            null
        } else {
            draft.copy(categoryIds = expandedCategoryIds).toFilter()
        }
        _filterPanelOpen.value = false
    }

    /** 清除筛选（同时清掉草稿，避免下次打开又带回来） */
    fun onFilterCleared() {
        filter.value = null
        _filterDraft.value = defaultDraft(statType.value)
    }

    /**
     * 直接应用一套筛选条件。
     *
     * @param newFilter 新的筛选条件；传 null 表示不筛选
     */
    fun onFilterApplied(newFilter: BillFilter?) {
        filter.value = newFilter
    }

    /**
     * 构造「全选」默认草稿：当前类型下全部一级分类 + 全部账户 + 未指定账户。
     */
    private fun defaultDraft(type: BillType): StatsFilterDraft {
        val topLevelIds = categories.value
            .filter { it.parentId == null && it.type == type }
            .map { it.id }
            .toSet()
        return StatsFilterDraft(
            type = type,
            categoryIds = topLevelIds,
            accountIds = accounts.value.map { it.id }.toSet(),
            includeUnspecifiedAccount = true
        )
    }

    private fun rangeOf(kind: DateRangeKind, anchorMillis: Long): DateRange {
        val raw = when (kind) {
            DateRangeKind.DAY -> TimeRange.day(anchorMillis, zone)
            DateRangeKind.WEEK -> TimeRange.week(anchorMillis, zone)
            DateRangeKind.MONTH -> TimeRange.month(anchorMillis, zone)
            DateRangeKind.YEAR -> TimeRange.year(anchorMillis, zone)
        }
        return DateRange(startMillis = raw.first, endMillis = raw.last)
    }

    private fun granularityOf(kind: DateRangeKind): StatsGranularity = when (kind) {
        DateRangeKind.DAY -> StatsGranularity.DAY
        DateRangeKind.WEEK -> StatsGranularity.DAY
        DateRangeKind.MONTH -> StatsGranularity.MONTH
        DateRangeKind.YEAR -> StatsGranularity.YEAR
    }

    /**
     * 周支出对比：以 [anchorMillis] 所在周为最后一桶，向前取 5 个自然周。
     *
     * 桶边界以周一 00:00 起算，与 [TimeRange.week] 口径一致；空周金额为 0，
     * 图表上仍然占位，方便用户直观看出「这周相对上周花多了还是少了」。
     */
    private fun weeklyBars(
        bills: List<Bill>,
        type: BillType,
        anchorMillis: Long
    ): List<WeeklyBar> {
        val anchorMonday = mondayOf(anchorMillis)
        val weeks = (4 downTo 0).map { anchorMonday.minusWeeks(it.toLong()) }
        val amounts = bills
            .filter { it.type == type }
            .groupBy { mondayOf(it.tradeTimeMillis) }
            .mapValues { entry -> entry.value.sumOf { it.amountCents } }
        return weeks.map { start ->
            val startMs = start.atStartOfDay(zone).toInstant().toEpochMilli()
            val endMs = start.plusDays(6)
                .atTime(23, 59, 59, 999_999_999)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
            WeeklyBar(
                startMillis = startMs,
                endMillis = endMs,
                amountCents = amounts[start] ?: 0L,
                label = weekLabel(start)
            )
        }
    }

    /** 毫秒 → 所在周的周一 */
    private fun mondayOf(epochMillis: Long): LocalDate {
        val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        return date.minusDays((date.dayOfWeek.value - 1).toLong())
    }

    private fun BillFilter.toDraft(): StatsFilterDraft = StatsFilterDraft(
        type = type,
        categoryIds = categoryIds,
        accountIds = accountIds,
        includeUnspecifiedAccount = includeUnspecifiedAccount
    )

    private data class StatsQuery(
        val range: DateRange,
        val granularity: StatsGranularity,
        val type: BillType,
        val filter: BillFilter?,
        val mode: CategoryStatsMode
    )
}
