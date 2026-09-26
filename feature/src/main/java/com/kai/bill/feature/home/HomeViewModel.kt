package com.kai.bill.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kai.bill.core.common.time.TimeRange
import com.kai.bill.domain.model.Account
import com.kai.bill.domain.model.Bill
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.Category
import com.kai.bill.domain.model.DateRange
import com.kai.bill.domain.model.SourceType
import com.kai.bill.domain.model.stats.BudgetProgress
import com.kai.bill.domain.model.stats.Overview
import com.kai.bill.domain.repository.AccountRepository
import com.kai.bill.domain.repository.CategoryRepository
import com.kai.bill.domain.repository.PendingBillRepository
import com.kai.bill.domain.time.Clock
import com.kai.bill.domain.time.DayTicker
import com.kai.bill.domain.usecase.bill.ObserveBillsUseCase
import com.kai.bill.domain.usecase.budget.ObserveBudgetProgressUseCase
import com.kai.bill.domain.usecase.stats.ObserveOverviewUseCase
import com.kai.bill.core.prefs.KaiPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

/**
 * 首页 ViewModel。
 *
 * 概览数字（累计消费 / 本月支出 / 本月收入 / 今日支出）走 [ObserveOverviewUseCase] 的
 * `StatsDao` SQL 聚合：与统计页共用同一个 UseCase，避免口径对不上（首页说 100、统计页说 120），
 * 也避免把所有账单拉进内存累加而拖慢首屏。
 *
 * 流水列表（[buildDailyGroups]）仍需要明细，所以 [ObserveBillsUseCase] 保留。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    observeBills: ObserveBillsUseCase,
    observeOverview: ObserveOverviewUseCase,
    observeBudgetProgress: ObserveBudgetProgressUseCase,
    categoryRepository: CategoryRepository,
    accountRepository: AccountRepository,
    pendingBillRepository: PendingBillRepository,
    private val dayTicker: DayTicker,
    private val clock: Clock,
    private val kaiPrefs: KaiPrefs
) : ViewModel() {

    private val zone = ZoneId.systemDefault()

    /** 全时段：用于「累计消费」。等价于不限制时间 */
    private val allTimeRange = DateRange(startMillis = 0L, endMillis = Long.MAX_VALUE)

    /**
     * 三个区间的概览：累计 / 本月 / 今日。
     *
     * 区间以 [DayTicker] 为上游重算：直接写 `rangeOf { … }` 只在**构造流时**算一次，
     * 跨日后首页会出现「流水列表已是新的一天、概览还是昨天」的错位（真机上就是预算卡的
     * 「今日已花」停在昨天）。挂上 ticker 后，跨日与每次重新订阅（切回前台）都用当下时刻重算。
     *
     * 先合成一个流再并入 [uiState]：`combine` 的 typed overload 最多 5 个源，
     * 把三个 overview 直接写进去会超限。
     */
    private val overviews: Flow<HomeOverviews> = dayTicker.ticks().flatMapLatest {
        combine(
            observeOverview(allTimeRange),
            observeOverview(rangeOf { TimeRange.month(it, zone) }),
            observeOverview(rangeOf { TimeRange.day(it, zone) })
        ) { total, month, today -> HomeOverviews(total, month, today) }
    }

    /** 总额预算进度，用于首页预算卡；无总额预算时为 null（首页不展示预算卡） */
    private val budgetProgressFlow: Flow<BudgetProgress?> =
        observeBudgetProgress().map { list -> list.firstOrNull { it.budget.isTotalBudget } }

    /**
     * 流水列表的「今日 / 昨日」标签在**每次发射时**用 `LocalDate.now(zone)` 重算，
     * 而 [overviews] 会在跨日时发射一次 → 日期标签与区间数字同时翻页，
     * 不会再出现「列表已是新的一天、预算卡还停在昨天」。
     */
    val uiState: StateFlow<HomeUiState> = combine(
        observeBills(allTimeRange),
        categoryRepository.observeAll(),
        accountRepository.observeAll(),
        overviews,
        budgetProgressFlow
    ) { bills, categories, accounts, overviews, budgetProgress ->
        HomeUiState(
            overview = HomeOverview(
                totalExpenseCents = overviews.total.expenseCents,
                monthExpenseCents = overviews.month.expenseCents,
                monthIncomeCents = overviews.month.incomeCents,
                todayExpenseCents = overviews.today.expenseCents
            ),
            budgetProgress = budgetProgress,
            dailyGroups = buildDailyGroups(bills, LocalDate.now(zone)),
            categories = categories,
            accounts = accounts,
            todayLabel = formatTodayLabel()
        )
    }
        // 分组按天聚合要扫全量账单，放后台线程避免拖慢首屏
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState()
        )

    /**
     * 监听服务断连预警：用户已开启自动采集（captureEnabled）但真实连接态
     * （notificationListenerEnabled）为 false，说明通知监听服务未真正运行
     * （常见于重装 App 或厂商清理后台），可能在静默漏采。
     */
    val captureWarning: StateFlow<Boolean> = kaiPrefs.captureState
        .map { it.captureEnabled && !it.notificationListenerEnabled }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    /**
     * 待确认账单条数：首页只在 > 0 时展示「有 N 笔待确认」卡片。
     *
     * 用 `observeCount()` 的 `COUNT(*)` 而不是拉列表算长度 —— 首页只需要一个数字，
     * 没必要把带通知原文的整表读进内存（待确认列表页才需要明细）。
     */
    val pendingCount: StateFlow<Int> = pendingBillRepository.observeCount()
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    private fun buildDailyGroups(bills: List<Bill>, today: LocalDate): List<DailyGroup> {
        return bills
            .groupBy { Instant.ofEpochMilli(it.tradeTimeMillis).atZone(zone).toLocalDate() }
            .toSortedMap(compareByDescending { it })
            .map { (date, dayBills) ->
                var expense = 0L
                var income = 0L
                dayBills.forEach { bill ->
                    // 退款是支出侧负项、不计收入：日汇总必须与上方概览同口径
                    expense += bill.signedExpenseCents ?: 0L
                    income += bill.signedIncomeCents ?: 0L
                }
                DailyGroup(
                    date = date,
                    dateLabel = formatDateLabel(date, today),
                    expenseCents = expense,
                    incomeCents = income,
                    bills = dayBills.sortedByDescending { it.tradeTimeMillis }
                )
            }
    }

    private fun rangeOf(block: (Long) -> LongRange): DateRange {
        val raw = block(clock.nowMillis())
        return DateRange(startMillis = raw.first, endMillis = raw.last)
    }

    private fun formatDateLabel(date: LocalDate, today: LocalDate): String {
        val dateText = "${date.monthValue}月${date.dayOfMonth}日"
        val suffix = when (date) {
            today -> "今日"
            today.minusDays(1) -> "昨日"
            else -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        }
        return "$dateText $suffix"
    }

    /** 顶栏「今日」后的日期文案，如「9月14日」。 */
    private fun formatTodayLabel(): String {
        val today = Instant.ofEpochMilli(clock.nowMillis()).atZone(zone).toLocalDate()
        return "${today.monthValue}月${today.dayOfMonth}日"
    }

    private data class HomeOverviews(
        val total: Overview,
        val month: Overview,
        val today: Overview
    )
}

/** 账单来源的展示文案 */
fun SourceType.sourceDisplayName(): String = when (this) {
    SourceType.NOTIFICATION -> "自动记账"
    SourceType.SMS -> "短信记账"
    SourceType.MANUAL -> "手动记账"
    SourceType.ACCESSIBILITY -> "分类识别"
    SourceType.SCREENSHOT -> "截图记账"
}
