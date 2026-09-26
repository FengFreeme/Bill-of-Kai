package com.kai.bill.core.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.kai.bill.core.db.entity.BillType
import com.kai.bill.core.db.model.AccountTotalRow
import com.kai.bill.core.db.model.CategoryTotalRow
import com.kai.bill.core.db.model.TrendBucketRow
import com.kai.bill.core.db.model.TypeTotalRow
import kotlinx.coroutines.flow.Flow

/**
 * 统计聚合查询。
 *
 * 三条共同前提，改动 SQL 时必须保持：
 * 1. 带类型的聚合一律写成 `(type = 'TRANSFER' OR countInStats = 1)`：
 *    支出 / 收入只统计 `countInStats = 1` 的流水；**转账不参与 `countInStats` 判断** ——
 *    转账行的该字段恒为 false（见 `Bill.isCounted`，转账本就被排除在收支之外），
 *    不放行的话「转账维度」永远是空的。这条例外直接写在 SQL 里，不由调用方传开关，
 *    少一个可能传错的参数，也让口径与查询放在一处。
 * 2. 一律用 `time BETWEEN ...` 而非 `createdAt` —— 补记场景要按**交易时间**归月
 * 3. 一律用 `COALESCE(SUM(...), 0)` —— SQLite 在无匹配行时 `SUM` 返回 NULL
 *
 * NOTE: `'TRANSFER'` 是 [BillType.TRANSFER] 在库中的**存储值**（见 `Converters`，枚举按 `name` 存 TEXT）。
 * 重命名该常量时必须同步这里 —— 字面量写错不会编译报错，只会让转账维度静默变空。
 *
 * 退款（`isRefund = 1`）在统计里属**支出维度的负项**：维度取
 * `CASE WHEN isRefund = 1 THEN 'EXPENSE' ELSE type END`，金额取
 * `CASE WHEN isRefund = 1 THEN -amountCents ELSE amountCents END`（钱退回来了，等于没花）。
 * 它与 `Bill.signedExpenseCents` 是同一套口径的两个实现，改一侧必须同步另一侧。
 *
 * 全部返回 `Flow`：Room 会监听相关表，记一笔账后统计自动刷新。
 */
@Dao
interface StatsDao {

    /**
     * 按账单类型聚合，用于首页概览的支出 / 收入 / 结余。
     *
     * 概览只关心收支，不看转账：所以这里**不带**上面那条例外，
     * 转账行会被 `countInStats = 1` 直接过滤掉。
     *
     * @param startMillis 统计区间起始毫秒（含）
     * @param endMillis 统计区间结束毫秒（含）
     * @return 每个类型一行；区间内无数据时不发射任何行
     */
    @Query(
        """
        SELECT CASE WHEN isRefund = 1 THEN 'EXPENSE' ELSE type END AS billType,
               COALESCE(SUM(CASE WHEN isRefund = 1 THEN -amountCents ELSE amountCents END), 0) AS totalCents
        FROM bill
        WHERE time BETWEEN :startMillis AND :endMillis AND countInStats = 1
        GROUP BY billType
        """
    )
    fun observeTypeTotals(
        startMillis: Long,
        endMillis: Long
    ): Flow<List<TypeTotalRow>>

    /**
     * 按分类聚合，用于饼图与分类排行。
     *
     * @param type 只统计支出或收入；分类本身已按类型区分，混入会算出错误占比
     * @return 按金额降序排列，最大的分类在最前
     */
    @Query(
        """
        SELECT categoryId AS categoryId,
               COALESCE(SUM(CASE WHEN isRefund = 1 THEN -amountCents ELSE amountCents END), 0) AS totalCents,
               COUNT(*) AS billCount
        FROM bill
        WHERE time BETWEEN :startMillis AND :endMillis
          AND (type = 'TRANSFER' OR countInStats = 1)
          AND CASE WHEN isRefund = 1 THEN 'EXPENSE' ELSE type END = :type
        GROUP BY categoryId
        ORDER BY totalCents DESC
        """
    )
    fun observeCategoryTotals(
        startMillis: Long,
        endMillis: Long,
        type: BillType
    ): Flow<List<CategoryTotalRow>>

    /**
     * 按账户聚合，用于账户维度统计。
     *
     * @return 按金额降序排列；`accountId` 为 null 表示「未指定账户」的流水
     */
    @Query(
        """
        SELECT accountId AS accountId,
               COALESCE(SUM(CASE WHEN isRefund = 1 THEN -amountCents ELSE amountCents END), 0) AS totalCents
        FROM bill
        WHERE time BETWEEN :startMillis AND :endMillis
          AND (type = 'TRANSFER' OR countInStats = 1)
          AND CASE WHEN isRefund = 1 THEN 'EXPENSE' ELSE type END = :type
        GROUP BY accountId
        ORDER BY totalCents DESC
        """
    )
    fun observeAccountTotals(
        startMillis: Long,
        endMillis: Long,
        type: BillType
    ): Flow<List<AccountTotalRow>>

    /**
     * 按自然日聚合，用于趋势折线。
     *
     * NOTE: 用 `strftime(..., 'localtime')` 而非 `time / 86400000` ——
     *       后者切出的是 UTC 日界，东八区会整体偏移 8 小时，把当晚的消费算到前一天。
     *
     * @return 按日期升序排列，桶标签形如 `2026-09-12`
     */
    @Query(
        """
        SELECT strftime('%Y-%m-%d', time / 1000, 'unixepoch', 'localtime') AS bucket,
               COALESCE(SUM(CASE WHEN isRefund = 1 THEN -amountCents ELSE amountCents END), 0) AS totalCents
        FROM bill
        WHERE time BETWEEN :startMillis AND :endMillis
          AND (type = 'TRANSFER' OR countInStats = 1)
          AND CASE WHEN isRefund = 1 THEN 'EXPENSE' ELSE type END = :type
        GROUP BY bucket
        ORDER BY bucket ASC
        """
    )
    fun observeDailyTrend(
        startMillis: Long,
        endMillis: Long,
        type: BillType
    ): Flow<List<TrendBucketRow>>

    /**
     * 按自然月聚合，用于「年度趋势」这类跨度较大的图表。
     *
     * @return 按月份升序排列，桶标签形如 `2026-09`
     */
    @Query(
        """
        SELECT strftime('%Y-%m', time / 1000, 'unixepoch', 'localtime') AS bucket,
               COALESCE(SUM(CASE WHEN isRefund = 1 THEN -amountCents ELSE amountCents END), 0) AS totalCents
        FROM bill
        WHERE time BETWEEN :startMillis AND :endMillis
          AND (type = 'TRANSFER' OR countInStats = 1)
          AND CASE WHEN isRefund = 1 THEN 'EXPENSE' ELSE type END = :type
        GROUP BY bucket
        ORDER BY bucket ASC
        """
    )
    fun observeMonthlyTrend(
        startMillis: Long,
        endMillis: Long,
        type: BillType
    ): Flow<List<TrendBucketRow>>
}
