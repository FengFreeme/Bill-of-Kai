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
 * 从明细账单做内存聚合，**只在「带筛选」或「子分类模式」时使用**。
 *
 * 为什么需要它：`StatsDao` 的聚合查询不接受分类 / 账户筛选参数。用静态 SQL 表达多选筛选
 * 会很别扭 —— Room 无法优雅处理「任意长度的 IN」与「空集合表示不过滤」这两种语义
 * （空 List 会生成语法错误的 `IN ()`）。而个人记账的单区间流水在千级，
 * 内存聚合这点开销完全可以忽略，换来的是「统计口径与明细列表完全一致」。
 *
 * **口径必须与 SQL 一致**（改动任一侧都要同步另一侧）：
 * 1. 参与聚合的判据与带符号金额都出自 `signedAmountIn`，与 `StatsDao` 里
 *    `(type = 'TRANSFER' OR countInStats = 1)` + `CASE WHEN isRefund = 1 ... END` 一一对应
 * 2. 一律按 [Bill.tradeTimeMillis] 归桶，不用 `createdAt`
 * 3. 金额恒为 `Long`（分）；**退款是负值**，因此求和一律用带符号金额，
 *    不能直接 `sumOf { it.amountCents }`
 */
internal object StatsAggregator {

    /**
     * 该账单在 [type] 维度里的带符号金额；null 表示不参与该维度。
     *
     * 支出 / 收入维度沿用 [Bill.signedExpenseCents] / [Bill.signedIncomeCents]（退款是支出侧负项、
     * 不计入收入）；转账维度反过来只看类型、不看 `countInStats`（那个开关对转账本是空操作）。
     * 返回「金额或 null」而不是「是否参与 + 金额」两个函数，避免出现「参与了但按 0 计入」。
     */
    private fun Bill.signedAmountIn(type: BillType): Long? = when (type) {
        BillType.TRANSFER -> if (this.type == BillType.TRANSFER) amountCents else null
        BillType.EXPENSE -> signedExpenseCents
        BillType.INCOME -> signedIncomeCents
    }

    /** 分类维度的聚合金额（含退款的负值），按金额降序 */
    fun categoryAmounts(bills: List<Bill>, type: BillType): List<CategoryAmount> =
        bills.asSequence()
            .mapNotNull { bill -> bill.signedAmountIn(type)?.let { bill to it } }
            .groupBy { (bill, _) -> bill.categoryId }
            .map { (categoryId, group) ->
                CategoryAmount(
                    categoryId = categoryId,
                    amountCents = group.sumOf { (_, signed) -> signed },
                    billCount = group.size
                )
            }
            .sortedByDescending { it.amountCents }

    /** 账户维度的聚合金额，按金额降序；`accountId` 为 null 单列一项，不并入任何账户 */
    fun accountAmounts(bills: List<Bill>, type: BillType): List<AccountAmount> =
        bills.asSequence()
            .mapNotNull { bill -> bill.signedAmountIn(type)?.let { bill to it } }
            .groupBy { (bill, _) -> bill.accountId }
            .map { (accountId, group) ->
                AccountAmount(
                    accountId = accountId,
                    amountCents = group.sumOf { (_, signed) -> signed }
                )
            }
            .sortedByDescending { it.amountCents }

    /**
     * 区间概览：支出 / 收入两侧一次遍历算完（转账天然不算，它是独立维度）。
     *
     * 两个和各取带符号金额：退款减支出、且不进收入 —— 同一笔不会两头都记。
     */
    fun overview(bills: List<Bill>): Overview {
        var expenseCents = 0L
        var incomeCents = 0L
        bills.forEach { bill ->
            expenseCents += bill.signedExpenseCents ?: 0L
            incomeCents += bill.signedIncomeCents ?: 0L
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
            .mapNotNull { bill -> bill.signedAmountIn(type)?.let { bill to it } }
            .groupBy { (bill, _) -> bucketStartMillis(bill.tradeTimeMillis, granularity, zone) }
            .map { (startMillis, group) ->
                TrendPoint(
                    startMillis = startMillis,
                    amountCents = group.sumOf { (_, signed) -> signed }
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
