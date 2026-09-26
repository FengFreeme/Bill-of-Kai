package com.kai.bill.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kai.bill.core.common.time.TimeRange
import com.kai.bill.domain.model.Bill
import com.kai.bill.domain.model.BillFilter
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.Category
import com.kai.bill.domain.model.DateRange
import com.kai.bill.domain.model.categoryIdsWithChildren
import com.kai.bill.domain.model.Account
import com.kai.bill.domain.model.stats.CategoryStat
import com.kai.bill.domain.repository.AccountRepository
import com.kai.bill.domain.repository.BillRepository
import com.kai.bill.domain.repository.CategoryRepository
import com.kai.bill.domain.time.Clock
import com.kai.bill.domain.time.DayTicker
import com.kai.bill.feature.home.DailyGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.abs

/**
 * 分类详情页 ViewModel。
 *
 * 口径：本分类**及其名下所有二级分类**在「本月」的流水；顶部 tab 切到某个子分类时只收窄口径。
 *
 * 固定「本月」而非继承统计页粒度：详情页是从排行点进来的下钻页面，用户关注「这个月这类花在哪」；
 * 跨页传粒度会让返回栈状态变复杂，收益只是省一次点击。真有「看某年的餐饮」需求再在页内加粒度切换。
 *
 * 「本月」与「今日 / 昨日」标签都由 [anchorMillis] 现算，因此需要 [dayTicker] 跨日前移一次，
 * 否则 App 跨日存活时页面会停在昨天（与统计页同一类问题）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoryDetailViewModel @Inject constructor(
    private val billRepository: BillRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val dayTicker: DayTicker,
    private val clock: Clock
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    private val categoryId = MutableStateFlow(0L)
    private val selectedChildId = MutableStateFlow<Long?>(null)

    /** 「本月」锚点（毫秒）：区间与「今日 / 昨日」标签都按它现算，跨日由 [dayTicker] 前移 */
    private val anchorMillis = MutableStateFlow(clock.nowMillis())

    init {
        viewModelScope.launch {
            dayTicker.ticks().collect { anchorMillis.value = clock.nowMillis() }
        }
    }

    private val allCategories = categoryRepository.observeAll()

    /** 流水渲染用的分类 / 账户映射；取全量，归档项也有历史账单在引用 */
    val categories: StateFlow<List<Category>> = allCategories
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    val accounts: StateFlow<List<Account>> = accountRepository.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    private val range: DateRange
        get() {
            val raw = TimeRange.month(anchorMillis.value, zone)
            return DateRange(startMillis = raw.first, endMillis = raw.last)
        }

    /** 根分类 + 其子分类 id：账单可能记在任一级上，口径必须同时包含两层 */
    private val scopedCategoryIds = combine(allCategories, categoryId) { all, id ->
        all.categoryIdsWithChildren(id)
    }

    /** 今日（按锚点算）：驱动「今日 / 昨日」标签，跨日后由 [anchorMillis] 一起刷新 */
    private val today: LocalDate
        get() = Instant.ofEpochMilli(anchorMillis.value).atZone(zone).toLocalDate()

    // 锚点并进来是为了跨日重订阅：区间与日期标签都在下游现算，没有它就不会重算
    private val bills = combine(scopedCategoryIds, anchorMillis) { ids, _ -> ids }
        .flatMapLatest { ids ->
            billRepository.observeBills(range, BillFilter(categoryIds = ids))
        }

    val uiState: StateFlow<CategoryDetailUiState> = combine(
        allCategories,
        categoryId,
        selectedChildId,
        bills
    ) { all, id, childId, allBills ->
        val categoryById = all.associateBy { it.id }
        val root = categoryById[id]

        // 只显示「有流水（净额为正）」的二级分类标签，避免空 tab 干扰
        val childAmounts = allBills.asSequence()
            .mapNotNull { bill -> bill.ownDimensionSignedCents?.let { bill.categoryId to it } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, signed) -> signed.sum() }
        val children = all.asSequence()
            .filter { it.parentId == id }
            .filter { (childAmounts[it.id] ?: 0L) > 0L }
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
            .toList()

        // 切到某个二级分类时只保留该分类的流水
        val scoped = if (childId == null) allBills else allBills.filter { it.categoryId == childId }
        // 净额口径：退款在本分类里是负项，合计与笔数都跟着走（笔数只算真正参与的那几笔）
        val counted = scoped.mapNotNull { bill -> bill.ownDimensionSignedCents?.let { bill to it } }
        val totalCents = counted.sumOf { (_, signed) -> signed }

        CategoryDetailUiState(
            categoryId = id,
            categoryName = root?.name.orEmpty(),
            childTabs = children,
            selectedChildId = childId,
            totalCents = totalCents,
            averageCents = if (counted.isEmpty()) 0L else totalCents / counted.size,
            billCount = counted.size,
            composition = compositionOf(scoped, root, categoryById),
            groups = toDailyGroups(scoped, today)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = CategoryDetailUiState()
    )

    /** 进入页面时指定要看的分类 */
    fun load(id: Long) {
        if (id != 0L) categoryId.value = id
    }

    /** 切换顶部 tab；传 null 回到「总览」，再次点击当前选中项同样回总览。 */
    fun onChildSelected(childId: Long?) {
        selectedChildId.value = if (selectedChildId.value == childId) null else childId
    }

    /**
     * 子分类构成。
     *
     * 贡献项可能落在三种 id 上：各二级分类、根分类自身（用户直接在父分类上记账）、
     * 以及已删除分类（历史账单仍在）。名称与颜色缺失时兜底，避免出现无名扇区。
     */
    private fun compositionOf(
        bills: List<Bill>,
        root: Category?,
        categoryById: Map<Long, Category>
    ): List<CategoryStat> {
        val counted = bills.mapNotNull { bill -> bill.ownDimensionSignedCents?.let { bill to it } }
        if (counted.isEmpty()) return emptyList()
        val netCents = counted.sumOf { (_, signed) -> signed }
        // 净额非正（整页只有退款）时没有构成可言：单条负值画不成饼，直接不展示
        if (netCents <= 0L) return emptyList()
        // 分目取绝对值之和：负项按量级参与占比，否则会算出负比率（环形图按比率铺角度）
        val ratioTotalCents = counted.sumOf { (_, signed) -> abs(signed) }
        if (ratioTotalCents == 0L) return emptyList()

        return counted
            .groupBy { (bill, _) -> bill.categoryId }
            .map { (categoryId, group) ->
                val category = categoryById[categoryId]
                val amountCents = group.sumOf { (_, signed) -> signed }
                CategoryStat(
                    categoryId = categoryId,
                    categoryName = category?.name ?: root?.name ?: UNKNOWN_CATEGORY_NAME,
                    colorHex = category?.colorHex ?: root?.colorHex ?: UNKNOWN_COLOR_HEX,
                    amountCents = amountCents,
                    // 分母已判过非 0，这里直接除即可（domain 的 ratioOf 是模块内可见，跨模块用不了）；
                    // 分子取绝对值：退款是负项，按量级参与占比，否则环形图会算出负角度
                    ratio = abs(amountCents).toFloat() / ratioTotalCents.toFloat(),
                    billCount = group.size
                )
            }
            .sortedByDescending { it.amountCents }
    }

    /**
     * 这笔账在**它自己的维度**里的带符号金额；null 表示不参与收支（转账、未计入统计）。
     *
     * 与 `StatsAggregator` 的 `signedAmountIn(type)` 是同一个概念，差别只在这里不需要传维度：
     * 分类详情页一整页只装一个分类的流水，维度由分类本身决定，退款则始终是支出侧的负项。
     */
    private val Bill.ownDimensionSignedCents: Long?
        get() = signedExpenseCents ?: signedIncomeCents

    /** @param today 「今日」参照日，由调用方按锚点传入，避免这里绕过注入的 [clock] 取系统时间 */
    private fun toDailyGroups(bills: List<Bill>, today: LocalDate): List<DailyGroup> {
        return bills.asSequence()
            .groupBy { Instant.ofEpochMilli(it.tradeTimeMillis).atZone(zone).toLocalDate() }
            .entries
            .sortedByDescending { it.key }
            .map { (date, dayBills) ->
                var expenseCents = 0L
                var incomeCents = 0L
                dayBills.forEach { bill ->
                    // 与首页日汇总同一口径：退款减支出、不进收入
                    val expense = bill.signedExpenseCents
                    if (expense != null) {
                        expenseCents += expense
                    } else {
                        incomeCents += bill.signedIncomeCents ?: 0L
                    }
                }
                DailyGroup(
                    date = date,
                    dateLabel = dateLabelOf(date, today),
                    expenseCents = expenseCents,
                    incomeCents = incomeCents,
                    bills = dayBills.sortedByDescending { it.tradeTimeMillis }
                )
            }
            .toList()
    }

    private fun dateLabelOf(date: LocalDate, today: LocalDate): String {
        val base = "${date.monthValue}月${date.dayOfMonth}日"
        return when (date) {
            today -> "$base 今日"
            today.minusDays(1) -> "$base 昨日"
            else -> base
        }
    }

    private companion object {
        const val UNKNOWN_CATEGORY_NAME = "未分类"
        const val UNKNOWN_COLOR_HEX = "#9E9E9E"
    }
}
