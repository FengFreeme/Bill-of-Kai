package com.kai.bill.domain.usecase.stats

import com.kai.bill.domain.model.BillFilter
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.DateRange
import com.kai.bill.domain.model.RefundCategory
import com.kai.bill.domain.model.childToParentIdMap
import com.kai.bill.domain.model.stats.CategoryAmount
import com.kai.bill.domain.model.stats.CategoryStat
import com.kai.bill.domain.model.stats.CategoryStatsMode
import com.kai.bill.domain.repository.BillRepository
import com.kai.bill.domain.repository.CategoryRepository
import com.kai.bill.domain.repository.StatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * 观察分类统计（饼图扇区 + 排行榜共用同一份数据）。
 *
 * SQL 的 `GROUP BY categoryId` 只返回 ID，这里 `combine` 分类流补齐名称与颜色，
 * 用 `associateBy` 建一次 Map 做 O(1) 查找 —— **不要逐条 `getById`**，那会退化成 N+1 查询。
 *
 * **层级处理**：
 * - 未筛选具体分类时，二级分类的金额**上卷到一级** —— 75 个二级分类直接画饼图
 *   会碎成一堆看不清的细条，而统计页看的是结构占比，不是明细；
 * - 已筛选具体分类时，保留二级明细 —— 此时用户已经把范围收窄了，
 *   再上卷就只剩一个 100% 的扇区，反而没有信息量。
 *
 * 比率 [CategoryStat.ratio] 在此统一算好，UI 只负责渲染。若把比率留给 UI 算，
 * 很容易出现一处按「总额」、另一处按「有分类的总额」，两个分母口径不一致，
 * 最终占比加起来不是 100%。
 *
 * **退款（负值）的两条特殊处理**：
 * 1. 不参与一级上卷，单列一条负值 —— 它挂在收入侧的「退款」分类上，
 *    上卷会把它并进「其他收入」，在支出构成里出现一个收入分类（见 [RefundCategory]）；
 * 2. 占比分母取各项**绝对值之和**（[absoluteTotalOf]），净额做分母会算出负比率，
 *    环形图按比率铺角度就崩了。合计金额 [CategoryStat.amountCents] 仍按净额，负号照常显示。
 */
class ObserveCategoryStatsUseCase @Inject constructor(
    private val statsRepository: StatsRepository,
    private val categoryRepository: CategoryRepository,
    private val billRepository: BillRepository
) {

    operator fun invoke(
        range: DateRange,
        type: BillType,
        filter: BillFilter? = null,
        mode: CategoryStatsMode = CategoryStatsMode.PARENT
    ): Flow<List<CategoryStat>> {
        // 主分类模式 + 无筛选：走 SQL GROUP BY，再按 parentId 上卷到一级；
        // 其余场景（子分类模式 或 有筛选）：走明细内存聚合，按 mode 决定是否上卷。
        val amounts: Flow<List<CategoryAmount>> = if (mode == CategoryStatsMode.PARENT && filter == null) {
            statsRepository.observeCategoryAmounts(range, type)
        } else {
            val billFilter = filter ?: BillFilter(
                type = type,
                countInStats = true
            )
            billRepository.observeBills(range, billFilter)
                .map { bills -> StatsAggregator.categoryAmounts(bills, type) }
        }

        // 取全量分类而不是 observeByType(type)：退款挂在收入侧的「退款」分类上，
        // 按类型取会让它在支出构成里找不到名字、退化成灰色「未分类」
        return combine(amounts, categoryRepository.observeAll()) { raw, categories ->
            val categoryById = categories.associateBy { it.id }
            val merged = if (mode == CategoryStatsMode.PARENT) {
                val parentMap = categories.childToParentIdMap()
                raw.groupBy { amount ->
                    // 退款不参与上卷：上卷会把它并进「其他收入」，支出构成里就多了个收入分类
                    if (amount.categoryId == RefundCategory.ID) {
                        amount.categoryId
                    } else {
                        parentMap[amount.categoryId] ?: amount.categoryId
                    }
                }
                    .map { (categoryId, group) ->
                        CategoryAmount(
                            categoryId = categoryId,
                            amountCents = group.sumOf { it.amountCents },
                            billCount = group.sumOf { it.billCount }
                        )
                    }
                    .sortedByDescending { it.amountCents }
            } else {
                raw.sortedByDescending { it.amountCents }
            }

            // 分母取绝对值之和：净额做分母时退款会算出负比率（见文件头注释）
            val ratioTotalCents = absoluteTotalOf(merged.map { it.amountCents })
            merged.map { amount ->
                val category = categoryById[amount.categoryId]
                CategoryStat(
                    categoryId = amount.categoryId,
                    // 分类被删除后聚合里仍有历史数据，兜底成灰色「未分类」，
                    // 不能让饼图出现无名无色、无法辨识的扇区
                    categoryName = category?.name ?: UNKNOWN_CATEGORY_NAME,
                    colorHex = category?.colorHex ?: UNKNOWN_COLOR_HEX,
                    amountCents = amount.amountCents,
                    ratio = ratioOf(amount.amountCents, ratioTotalCents),
                    billCount = amount.billCount
                )
            }
        }
    }

    private companion object {
        const val UNKNOWN_CATEGORY_NAME = "未分类"
        const val UNKNOWN_COLOR_HEX = "#9E9E9E"
    }
}
