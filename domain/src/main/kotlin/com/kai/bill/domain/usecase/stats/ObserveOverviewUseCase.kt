package com.kai.bill.domain.usecase.stats

import com.kai.bill.domain.model.BillFilter
import com.kai.bill.domain.model.DateRange
import com.kai.bill.domain.model.stats.Overview
import com.kai.bill.domain.repository.BillRepository
import com.kai.bill.domain.repository.StatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * 观察区间概览（支出 / 收入 / 结余）。
 *
 * 首页「累计消费」与统计页概览**必须共用本用例**：两处若各查一次，
 * 很容易出现首页说支出 100、统计页说 120 这种对不上的问题。
 *
 * 无筛选时走 [StatsRepository] 的 SQL 聚合；带筛选时改走明细的内存聚合
 * （[StatsAggregator]），保证概览与流水列表口径一致。
 */
class ObserveOverviewUseCase @Inject constructor(
    private val statsRepository: StatsRepository,
    private val billRepository: BillRepository
) {

    operator fun invoke(range: DateRange, filter: BillFilter? = null): Flow<Overview> =
        if (filter == null) {
            statsRepository.observeOverview(range)
        } else {
            billRepository.observeBills(range, filter)
                .map { bills -> StatsAggregator.overview(bills) }
        }
}
