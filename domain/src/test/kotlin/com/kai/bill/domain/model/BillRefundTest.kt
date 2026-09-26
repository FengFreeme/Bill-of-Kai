package com.kai.bill.domain.model

import com.kai.bill.domain.usecase.stats.StatsAggregator
import com.kai.bill.domain.usecase.stats.absoluteTotalOf
import com.kai.bill.domain.usecase.stats.ratioOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 退款口径的单测 —— 覆盖「带符号金额」与内存聚合两条路径。
 *
 * SQL 侧（`StatsDao` 里的 `CASE WHEN isRefund = 1`）无法在纯 JVM 里跑，
 * 因此这里钉住 Kotlin 侧的语义，两边必须一致（见 `StatsAggregator` 的文件头注释）。
 */
class BillRefundTest {

    @Test
    fun `普通支出-支出为正-不进收入`() {
        val bill = bill(amountCents = 8850L, type = BillType.EXPENSE, categoryId = 12L)

        assertEquals(8850L, bill.signedExpenseCents)
        assertNull(bill.signedIncomeCents)
    }

    @Test
    fun `普通收入-收入为正-不进支出`() {
        val bill = bill(amountCents = 20000L, type = BillType.INCOME, categoryId = 93L)

        assertNull(bill.signedExpenseCents)
        assertEquals(20000L, bill.signedIncomeCents)
    }

    @Test
    fun `退款-支出为负-且不计收入`() {
        val refund = bill(
            amountCents = 1239L,
            type = BillType.INCOME,
            categoryId = 24L,
            isRefund = true
        )

        assertEquals(-1239L, refund.signedExpenseCents)
        assertNull(refund.signedIncomeCents)
    }

    @Test
    fun `退款但未计入统计-两侧都不参与`() {
        val refund = bill(
            amountCents = 1239L,
            type = BillType.INCOME,
            categoryId = 24L,
            isRefund = true,
            countInStats = false
        )

        assertNull(refund.signedExpenseCents)
        assertNull(refund.signedIncomeCents)
    }

    @Test
    fun `未计入统计的支出-不参与支出`() {
        val bill = bill(
            amountCents = 8850L,
            type = BillType.EXPENSE,
            categoryId = 12L,
            countInStats = false
        )

        assertNull(bill.signedExpenseCents)
    }

    @Test
    fun `转账-两侧都不参与`() {
        val bill = bill(amountCents = 500000L, type = BillType.TRANSFER, categoryId = 20L)

        assertNull(bill.signedExpenseCents)
        assertNull(bill.signedIncomeCents)
    }

    // ---------------- 内存聚合 ----------------

    @Test
    fun `聚合概览-退款冲抵支出且不加收入`() {
        val bills = listOf(
            bill(amountCents = 9000L, type = BillType.EXPENSE, categoryId = 25L),
            bill(amountCents = 4200L, type = BillType.EXPENSE, categoryId = 12L),
            // 这笔退款是 9000 那笔的逆操作，归属已经改写成了 25「购物」
            bill(amountCents = 9000L, type = BillType.INCOME, categoryId = 25L, isRefund = true),
            bill(amountCents = 30000L, type = BillType.INCOME, categoryId = 93L)
        )

        val overview = StatsAggregator.overview(bills)

        // 支出 = 9000 + 4200 - 9000（退款）；收入只有 30000，退款不算收入
        assertEquals(4200L, overview.expenseCents)
        assertEquals(30000L, overview.incomeCents)
    }

    @Test
    fun `聚合分类-退款抵在原分类上`() {
        val bills = listOf(
            bill(amountCents = 9000L, type = BillType.EXPENSE, categoryId = 25L),
            bill(amountCents = 9000L, type = BillType.INCOME, categoryId = 25L, isRefund = true)
        )

        val categories = StatsAggregator.categoryAmounts(bills, BillType.EXPENSE)

        // 原分类净额归零，且只剩它一项（退款没有额外造出一个分类）
        assertEquals(1, categories.size)
        assertEquals(25L, categories.single().categoryId)
        assertEquals(0L, categories.single().amountCents)
    }

    @Test
    fun `聚合账户-退款从原账户里扣回`() {
        val bills = listOf(
            bill(amountCents = 9000L, type = BillType.EXPENSE, categoryId = 25L, accountId = 2L),
            bill(amountCents = 9000L, type = BillType.INCOME, categoryId = 25L, accountId = 2L, isRefund = true)
        )

        val accounts = StatsAggregator.accountAmounts(bills, BillType.EXPENSE)

        assertEquals(1, accounts.size)
        assertEquals(0L, accounts.single().amountCents)
    }

    // ---------------- 占比口径 ----------------

    @Test
    fun `占比-退款按量级参与且不为负`() {
        val amounts = listOf(9000L, -3000L, 3000L)

        val total = absoluteTotalOf(amounts)

        assertEquals(15000L, total)
        assertEquals(0.6f, ratioOf(9000L, total), 0.0001f)
        // 负项也要给出正的占比：负比率会让环形图算出负角度
        assertEquals(0.2f, ratioOf(-3000L, total), 0.0001f)
    }

    @Test
    fun `占比-全为正数时与净额口径一致`() {
        val amounts = listOf(9000L, 3000L)

        val total = absoluteTotalOf(amounts)

        assertEquals(12000L, total)
        assertEquals(0.75f, ratioOf(9000L, total), 0.0001f)
    }

    private fun bill(
        amountCents: Long,
        type: BillType,
        categoryId: Long,
        accountId: Long? = null,
        isRefund: Boolean = false,
        countInStats: Boolean = true
    ): Bill = Bill(
        id = 1L,
        amountCents = amountCents,
        type = type,
        countInStats = countInStats,
        isRefund = isRefund,
        categoryId = categoryId,
        accountId = accountId,
        merchant = null,
        note = null,
        tradeTimeMillis = 1_700_000_000_000L,
        source = SourceType.NOTIFICATION,
        rawText = null,
        dedupHash = "hash-$amountCents-$categoryId-$type-$isRefund",
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L
    )
}
