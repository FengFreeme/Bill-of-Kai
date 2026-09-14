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
 * 1. 一律带 `countInStats = 1` —— 转账、还款、待报销的流水不得进入统计与预算
 * 2. 一律用 `time BETWEEN ...` 而非 `createdAt` —— 补记场景要按**交易时间**归月
 * 3. 一律用 `COALESCE(SUM(...), 0)` —— SQLite 在无匹配行时 `SUM` 返回 NULL
 *
 * 全部返回 `Flow`：Room 会监听相关表，记一笔账后统计自动刷新。
 */
@Dao
interface StatsDao {

    /**
     * 按账单类型聚合，用于首页概览的支出 / 收入 / 结余。
     *
     * @param startMillis 统计区间起始毫秒（含）
     * @param endMillis 统计区间结束毫秒（含）
     * @return 每个类型一行；区间内无数据时不发射任何行
     */
    @Query(
        """
        SELECT type AS billType, COALESCE(SUM(amountCents), 0) AS totalCents
        FROM bill
        WHERE time BETWEEN :startMillis AND :endMillis AND countInStats = 1
        GROUP BY type
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
               COALESCE(SUM(amountCents), 0) AS totalCents,
               COUNT(*) AS billCount
        FROM bill
        WHERE time BETWEEN :startMillis AND :endMillis
          AND countInStats = 1
          AND type = :type
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
        SELECT accountId AS accountId, COALESCE(SUM(amountCents), 0) AS totalCents
        FROM bill
        WHERE time BETWEEN :startMillis AND :endMillis
          AND countInStats = 1
          AND type = :type
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
               COALESCE(SUM(amountCents), 0) AS totalCents
        FROM bill
        WHERE time BETWEEN :startMillis AND :endMillis
          AND countInStats = 1
          AND type = :type
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
               COALESCE(SUM(amountCents), 0) AS totalCents
        FROM bill
        WHERE time BETWEEN :startMillis AND :endMillis
          AND countInStats = 1
          AND type = :type
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
