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
import com.kai.bill.feature.home.DailyGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * 分类详情页 ViewModel。
 *
 * 口径：本分类**及其名下所有二级分类**在「本月」的流水。
 * 顶部 tab 切到某个子分类时，只把口径收窄到那一个子分类，其余逻辑不变。
 *
 * 为什么固定「本月」而不是继承统计页的粒度：详情页是从排行点进来的下钻页面，
 * 用户关注的是「这个月这类花在哪了」。跨页传递粒度会让返回栈里的状态变复杂，
 * 而收益只是省一次点击；等真有「看某年的餐饮」这类诉求，再在页内加粒度切换即可。
 *
 * @property billRepository 账单仓储（明细）
 * @property categoryRepository 分类仓储
 * @property clock 可注入时间源
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoryDetailViewModel @Inject constructor(
    private val billRepository: BillRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val clock: Clock
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    private val categoryId = MutableStateFlow(0L)
    private val selectedChildId = MutableStateFlow<Long?>(null)

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
            val raw = TimeRange.month(clock.nowMillis(), zone)
            return DateRange(startMillis = raw.first, endMillis = raw.last)
        }

    /** 根分类 + 其子分类 id：账单可能记在任一级上，口径必须同时包含两层 */
    private val scopedCategoryIds = combine(allCategories, categoryId) { all, id ->
        all.categoryIdsWithChildren(id)
    }

    private val bills = scopedCategoryIds.flatMapLatest { ids ->
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

        // 只显示「有计入统计流水」的二级分类标签，避免空 tab 干扰
        val childAmounts = allBills.asSequence()
            .filter { it.isCounted }
            .groupBy { it.categoryId }
            .mapValues { entry -> entry.value.sumOf { it.amountCents } }
        val children = all.asSequence()
            .filter { it.parentId == id }
            .filter { (childAmounts[it.id] ?: 0L) > 0L }
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
            .toList()

        // 切到某个二级分类时只保留该分类的流水
        val scoped = if (childId == null) allBills else allBills.filter { it.categoryId == childId }
        val counted = scoped.filter { it.isCounted }
        val totalCents = counted.sumOf { it.amountCents }

        CategoryDetailUiState(
            categoryId = id,
            categoryName = root?.name.orEmpty(),
            childTabs = children,
            selectedChildId = childId,
            totalCents = totalCents,
            averageCents = if (counted.isEmpty()) 0L else totalCents / counted.size,
            billCount = counted.size,
            composition = compositionOf(scoped, root, categoryById),
            groups = toDailyGroups(scoped)
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

    /**
     * 切换顶部 tab；传 null 表示回到「总览」。
     * 再次点击当前已选中的 tab 会取消选中，回到总览。
     */
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
        val counted = bills.filter { it.isCounted }
        if (counted.isEmpty()) return emptyList()
        val totalCents = counted.sumOf { it.amountCents }
        if (totalCents <= 0L) return emptyList()

        return counted.groupBy { it.categoryId }
            .map { (categoryId, group) ->
                val category = categoryById[categoryId]
                val amountCents = group.sumOf { it.amountCents }
                CategoryStat(
                    categoryId = categoryId,
                    categoryName = category?.name ?: root?.name ?: UNKNOWN_CATEGORY_NAME,
                    colorHex = category?.colorHex ?: root?.colorHex ?: UNKNOWN_COLOR_HEX,
                    amountCents = amountCents,
                    ratio = amountCents.toFloat() / totalCents.toFloat(),
                    billCount = group.size
                )
            }
            .sortedByDescending { it.amountCents }
    }

    private fun toDailyGroups(bills: List<Bill>): List<DailyGroup> {
        val today = LocalDate.now(zone)
        return bills.asSequence()
            .groupBy { Instant.ofEpochMilli(it.tradeTimeMillis).atZone(zone).toLocalDate() }
            .entries
            .sortedByDescending { it.key }
            .map { (date, dayBills) ->
                var expenseCents = 0L
                var incomeCents = 0L
                dayBills.forEach { bill ->
                    if (!bill.isCounted) return@forEach
                    when (bill.type) {
                        BillType.EXPENSE -> expenseCents += bill.amountCents
                        BillType.INCOME -> incomeCents += bill.amountCents
                        BillType.TRANSFER -> Unit
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
