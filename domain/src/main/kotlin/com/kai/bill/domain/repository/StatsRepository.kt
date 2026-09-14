package com.kai.bill.domain.repository

import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.DateRange
import com.kai.bill.domain.model.stats.AccountAmount
import com.kai.bill.domain.model.stats.CategoryAmount
import com.kai.bill.domain.model.stats.Overview
import com.kai.bill.domain.model.stats.TrendPoint
import kotlinx.coroutines.flow.Flow

/**
 * 统计聚合仓储。
 *
 * 与 [BillRepository] 分开：统计是**聚合查询**（`GROUP BY` + `SUM`），
 * 账单仓储是明细查询，两者的调用频率与优化方向完全不同，混在一起会让
 * 「只要一列汇总数字」的场景也背上明细字段的开销。
 *
 * 只返回领域模型，**不暴露 `core:db` 的 Row 类型**：domain 是纯 JVM 模块，
 * 依赖里没有 Room，Row → 领域的映射在 `data` 层的 Impl 内完成。
 *
 * 全部返回 [Flow]：Room 会监听 `bill` 表，记一笔账后统计自动刷新，无需手动重试。
 *
 * 聚合口径由 SQL 保证（改动 SQL 时必须保持）：
 * 1. `countInStats = 1` —— 转账、还款、待报销不进统计
 * 2. 按 `time`（交易时间）而非 `createdAt` —— 补记场景要按交易时间归月
 * 3. `COALESCE(SUM(...), 0)` —— 无匹配行时 SQLite 的 SUM 返回 NULL
 */
interface StatsRepository {

    /**
     * 观察区间概览（支出 / 收入 / 结余）。
     *
     * 只包含计入统计的账单，转账还款不计入，否则「本月支出」会被还信用卡撑得虚高。
     *
     * @param range 统计区间
     * @return 概览流；区间内无流水时发射 [Overview.EMPTY] 的等值对象
     */
    fun observeOverview(range: DateRange): Flow<Overview>

    /**
     * 观察分类维度的聚合金额（不含分类名与颜色）。
     *
     * @param range 统计区间
     * @param type 只统计支出或收入；分类本身已按类型区分，混入会算出错误占比
     * @return 按金额降序排列
     */
    fun observeCategoryAmounts(range: DateRange, type: BillType): Flow<List<CategoryAmount>>

    /**
     * 观察账户维度的聚合金额。
     *
     * @param range 统计区间
     * @param type 只统计支出或收入
     * @return 按金额降序；[AccountAmount.accountId] 为 null 表示「未指定账户」
     */
    fun observeAccountAmounts(range: DateRange, type: BillType): Flow<List<AccountAmount>>

    /**
     * 观察按自然日聚合的趋势。
     *
     * @return 按日期升序；每个点的 [TrendPoint.startMillis] 为该日 00:00（用户时区）
     */
    fun observeDailyTrend(range: DateRange, type: BillType): Flow<List<TrendPoint>>

    /**
     * 观察按自然月聚合的趋势，用于年度这类跨度较大的区间。
     *
     * @return 按月份升序；每个点的 [TrendPoint.startMillis] 为该月 1 日 00:00（用户时区）
     */
    fun observeMonthlyTrend(range: DateRange, type: BillType): Flow<List<TrendPoint>>
}
