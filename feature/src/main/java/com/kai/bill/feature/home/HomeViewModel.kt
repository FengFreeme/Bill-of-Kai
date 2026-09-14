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
import com.kai.bill.domain.time.Clock
import com.kai.bill.domain.usecase.bill.ObserveBillsUseCase
import com.kai.bill.domain.usecase.budget.ObserveBudgetProgressUseCase
import com.kai.bill.domain.usecase.stats.ObserveOverviewUseCase
import com.kai.bill.core.prefs.KaiPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
 * M2：概览数字（累计消费 / 本月支出 / 本月收入 / 今日支出）改由
 * [ObserveOverviewUseCase] 走 `StatsDao` 的 SQL 聚合，替换 M1 的内存全量遍历。
 *
 * 这样做有两个好处：
 * 1. **口径统一**：与统计页共用同一个 UseCase，不会出现首页说支出 100、
 *    统计页说 120 这种对不上的情况
 * 2. **不再扫全表**：原来要把所有账单拉到内存里累加，账单多了会拖慢首屏
 *
 * 流水列表（[buildDailyGroups]）仍需要明细数据，所以 [ObserveBillsUseCase] 保留。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    observeBills: ObserveBillsUseCase,
    observeOverview: ObserveOverviewUseCase,
    observeBudgetProgress: ObserveBudgetProgressUseCase,
    categoryRepository: CategoryRepository,
    accountRepository: AccountRepository,
    private val clock: Clock,
    private val kaiPrefs: KaiPrefs
) : ViewModel() {

    private val zone = ZoneId.systemDefault()

    /** 全时段：用于「累计消费」。等价于不限制时间 */
    private val allTimeRange = DateRange(startMillis = 0L, endMillis = Long.MAX_VALUE)

    /**
     * 三个区间的概览：累计 / 本月 / 今日。
     *
     * 先合成一个流再并入 [uiState]：`combine` 的 typed overload 最多 5 个源，
     * 把三个 overview 直接写进去会超限。
     */
    private val overviews: Flow<HomeOverviews> = combine(
        observeOverview(allTimeRange),
        observeOverview(rangeOf { TimeRange.month(it, zone) }),
        observeOverview(rangeOf { TimeRange.day(it, zone) })
    ) { total, month, today -> HomeOverviews(total, month, today) }

    /** 总额预算进度，用于首页预算卡；无总额预算时为 null（首页不展示预算卡） */
    private val budgetProgressFlow: Flow<BudgetProgress?> =
        observeBudgetProgress().map { list -> list.firstOrNull { it.budget.isTotalBudget } }

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
     * 监听服务断连预警（M6 保活/异常排查）：
     * 用户已开启自动采集（captureEnabled）但真实连接态（notificationListenerEnabled）为 false，
     * 说明通知监听服务未真正运行（常见于重装 App 或厂商清理后台），可能在静默漏采。
     * 由 [com.kai.bill.core.prefs.CaptureState] 提供真实连接态。
     */
    val captureWarning: StateFlow<Boolean> = kaiPrefs.captureState
        .map { it.captureEnabled && !it.notificationListenerEnabled }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    private fun buildDailyGroups(bills: List<Bill>, today: LocalDate): List<DailyGroup> {
        return bills
            .groupBy { Instant.ofEpochMilli(it.tradeTimeMillis).atZone(zone).toLocalDate() }
            .toSortedMap(compareByDescending { it })
            .map { (date, dayBills) ->
                var expense = 0L
                var income = 0L
                dayBills.forEach { bill ->
                    if (bill.countInStats) {
                        when (bill.type) {
                            BillType.EXPENSE -> expense += bill.amountCents
                            BillType.INCOME -> income += bill.amountCents
                            BillType.TRANSFER -> { }
                        }
                    }
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
}
