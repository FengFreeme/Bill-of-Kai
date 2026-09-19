package com.kai.bill.data.repository

import com.kai.bill.core.db.dao.StatsDao
import com.kai.bill.core.db.model.TrendBucketRow
import com.kai.bill.data.mapper.toDomain
import com.kai.bill.data.mapper.toEntity
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.DateRange
import com.kai.bill.domain.model.stats.AccountAmount
import com.kai.bill.domain.model.stats.CategoryAmount
import com.kai.bill.domain.model.stats.Overview
import com.kai.bill.domain.model.stats.TrendPoint
import com.kai.bill.domain.repository.StatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [StatsRepository] 的 Room 实现。
 *
 * 注入 [StatsDao]，把 `core:db` 的 Row 映射成领域模型；自身不放业务逻辑，
 * 聚合口径全部由 [StatsDao] 的 SQL 保证（`(type = 'TRANSFER' OR countInStats = 1)`、
 * 按 `time` 而非 `createdAt`、`COALESCE(SUM, 0)`）—— 这几条一旦被破坏，
 * 转账还款就会混进「本月支出」，修起来要翻整个统计页。
 *
 * 「转账维度不看 `countInStats`」这条例外已下沉到 SQL，本层不再传任何开关。
 */
@Singleton
class StatsRepositoryImpl @Inject constructor(
    private val statsDao: StatsDao
) : StatsRepository {

    override fun observeOverview(range: DateRange): Flow<Overview> =
        statsDao.observeTypeTotals(
            startMillis = range.startMillis,
            endMillis = range.endMillis
        ).map { rows ->
            var expenseCents = 0L
            var incomeCents = 0L
            rows.forEach { row ->
                when (row.billType.toDomain()) {
                    BillType.EXPENSE -> expenseCents += row.totalCents
                    BillType.INCOME -> incomeCents += row.totalCents
                    // 转账不进概览：还信用卡、账户互转都不是真实支出
                    BillType.TRANSFER -> Unit
                }
            }
            Overview(expenseCents = expenseCents, incomeCents = incomeCents)
        }

    override fun observeCategoryAmounts(
        range: DateRange,
        type: BillType
    ): Flow<List<CategoryAmount>> =
        statsDao.observeCategoryTotals(
            startMillis = range.startMillis,
            endMillis = range.endMillis,
            type = type.toEntity()
        ).map { rows ->
            rows.map { row ->
                CategoryAmount(
                    categoryId = row.categoryId,
                    amountCents = row.totalCents,
                    billCount = row.billCount
                )
            }
        }

    override fun observeAccountAmounts(
        range: DateRange,
        type: BillType
    ): Flow<List<AccountAmount>> =
        statsDao.observeAccountTotals(
            startMillis = range.startMillis,
            endMillis = range.endMillis,
            type = type.toEntity()
        ).map { rows ->
            rows.map { row ->
                AccountAmount(
                    accountId = row.accountId,
                    amountCents = row.totalCents
                )
            }
        }

    override fun observeDailyTrend(
        range: DateRange,
        type: BillType
    ): Flow<List<TrendPoint>> =
        statsDao.observeDailyTrend(
            startMillis = range.startMillis,
            endMillis = range.endMillis,
            type = type.toEntity()
        ).map { rows -> rows.map { it.toTrendPoint(monthly = false) } }

    override fun observeMonthlyTrend(
        range: DateRange,
        type: BillType
    ): Flow<List<TrendPoint>> =
        statsDao.observeMonthlyTrend(
            startMillis = range.startMillis,
            endMillis = range.endMillis,
            type = type.toEntity()
        ).map { rows -> rows.map { it.toTrendPoint(monthly = true) } }

    /**
     * Row → 趋势点。
     *
     * [StatsDao] 返回的 `bucket` 是 `strftime` 出的字符串（`2026-09-12` / `2026-09`），
     * 在这里还原成毫秒起点，保证「数据层不硬编码显示格式」——
     * UI 想用「9/12」还是「9月12日」由 UI 自己决定。
     */
    private fun TrendBucketRow.toTrendPoint(monthly: Boolean): TrendPoint =
        TrendPoint(
            startMillis = bucketToStartMillis(bucket = bucket, monthly = monthly),
            amountCents = totalCents
        )

    /**
     * 桶字符串 → 毫秒起点。
     *
     * 日桶取当天 00:00，月桶取当月 1 日 00:00，**都用用户时区而非 UTC**：
     * SQL 的 `strftime` 带了 `localtime` 修饰符，这里若用 UTC 反解，
     * 东八区的桶会整体偏移 8 小时，把当晚消费算到前一天。
     *
     * 解析失败返回 0：bucket 由 SQLite 生成，理论上不会非法，
     * 但兜住可以避免一条脏数据把整条趋势流打断（Flow 一旦抛错就永久终止）。
     */
    private fun bucketToStartMillis(bucket: String, monthly: Boolean): Long =
        runCatching {
            val date = if (monthly) LocalDate.parse("$bucket-01") else LocalDate.parse(bucket)
            date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrDefault(0L)
}
