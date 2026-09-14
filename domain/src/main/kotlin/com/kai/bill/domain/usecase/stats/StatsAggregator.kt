package com.kai.bill.domain.usecase.stats

import com.kai.bill.domain.model.Bill
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.stats.AccountAmount
import com.kai.bill.domain.model.stats.CategoryAmount
import com.kai.bill.domain.model.stats.Overview
import com.kai.bill.domain.model.stats.StatsGranularity
import com.kai.bill.domain.model.stats.TrendPoint
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * 从明细账单做内存聚合，**只在「带筛选」时使用**。
 *
 * 为什么需要它：`StatsDao` 的聚合查询不接受分类 / 账户筛选参数。用静态 SQL 表达多选筛选
 * 会很别扭 —— Room 无法优雅处理「任意长度的 IN」与「空集合表示不过滤」这两种语义
 * （空 List 会生成语法错误的 `IN ()`）。而个人记账的单区间流水在千级，
 * 内存聚合这点开销完全可以忽略，换来的是「统计口径与明细列表完全一致」。
 *
 * **口径必须与 SQL 一致**（改动任一侧都要同步另一侧）：
 * 1. 只统计 [Bill.isCounted]（即 `countInStats` 且类型为支出 / 收入），转账还款不进统计
 * 2. 一律按 [Bill.tradeTimeMillis] 归桶，不用 `createdAt`
 * 3. 金额恒为 `Long`（分）
 */
internal object StatsAggregator {

    /** 分类维度的聚合金额，按金额降序 */
    fun categoryAmounts(bills: List<Bill>, type: BillType): List<CategoryAmount> =
        bills.asSequence()
            .filter { it.isCounted && it.type == type }
            .groupBy { it.categoryId }
            .map { (categoryId, group) ->
                CategoryAmount(
                    categoryId = categoryId,
                    amountCents = group.sumOf { it.amountCents },
                    billCount = group.size
                )
            }
            .sortedByDescending { it.amountCents }

    /** 账户维度的聚合金额，按金额降序；`accountId` 为 null 单列一项，不并入任何账户 */
    fun accountAmounts(bills: List<Bill>, type: BillType): List<AccountAmount> =
        bills.asSequence()
            .filter { it.isCounted && it.type == type }
            .groupBy { it.accountId }
            .map { (accountId, group) ->
                AccountAmount(
                    accountId = accountId,
                    amountCents = group.sumOf { it.amountCents }
                )
            }
            .sortedByDescending { it.amountCents }

    /** 区间概览：支出 / 收入两侧一次遍历算完 */
    fun overview(bills: List<Bill>): Overview {
        var expenseCents = 0L
        var incomeCents = 0L
        bills.forEach { bill ->
            if (!bill.isCounted) return@forEach
            when (bill.type) {
                BillType.EXPENSE -> expenseCents += bill.amountCents
                BillType.INCOME -> incomeCents += bill.amountCents
                BillType.TRANSFER -> Unit
            }
        }
        return Overview(expenseCents = expenseCents, incomeCents = incomeCents)
    }

    /** 趋势：年度按自然月聚合，日 / 月区间按自然日聚合，按时间升序 */
    fun trend(
        bills: List<Bill>,
        type: BillType,
        granularity: StatsGranularity,
        zone: ZoneId
    ): List<TrendPoint> =
        bills.asSequence()
            .filter { it.isCounted && it.type == type }
            .groupBy { bucketStartMillis(it.tradeTimeMillis, granularity, zone) }
            .map { (startMillis, group) ->
                TrendPoint(
                    startMillis = startMillis,
                    amountCents = group.sumOf { it.amountCents }
                )
            }
            .sortedBy { it.startMillis }

    private fun bucketStartMillis(
        millis: Long,
        granularity: StatsGranularity,
        zone: ZoneId
    ): Long = if (granularity == StatsGranularity.YEAR) {
        YearMonth.from(Instant.ofEpochMilli(millis).atZone(zone))
            .atDay(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    } else {
        Instant.ofEpochMilli(millis)
            .atZone(zone)
            .toLocalDate()
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    }
}
