package com.kai.bill.domain.usecase.stats

import com.kai.bill.domain.model.BillFilter
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.DateRange
import com.kai.bill.domain.model.stats.StatsGranularity
import com.kai.bill.domain.model.stats.TrendPoint
import com.kai.bill.domain.repository.BillRepository
import com.kai.bill.domain.repository.StatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import javax.inject.Inject

/**
 * 观察指定粒度的趋势点。
 *
 * 按粒度选择聚合方式：
 * - 日 / 月区间：按自然日聚合（[StatsRepository.observeDailyTrend]）
 * - 年区间：按自然月聚合（[StatsRepository.observeMonthlyTrend]），
 *   否则 365 个数据点既画不下、也没有阅读价值
 *
 * 带筛选时改走明细的内存聚合（[StatsAggregator]），分桶规则与 SQL 侧保持一致。
 */
class ObserveTrendUseCase @Inject constructor(
    private val statsRepository: StatsRepository,
    private val billRepository: BillRepository
) {

    operator fun invoke(
        range: DateRange,
        type: BillType,
        granularity: StatsGranularity,
        filter: BillFilter? = null
    ): Flow<List<TrendPoint>> {
        if (filter != null) {
            val zone = ZoneId.systemDefault()
            return billRepository.observeBills(range, filter)
                .map { bills -> StatsAggregator.trend(bills, type, granularity, zone) }
        }
        return when (granularity) {
            StatsGranularity.YEAR ->
                statsRepository.observeMonthlyTrend(range, type)

            StatsGranularity.DAY,
            StatsGranularity.MONTH ->
                statsRepository.observeDailyTrend(range, type)
        }
    }
}
