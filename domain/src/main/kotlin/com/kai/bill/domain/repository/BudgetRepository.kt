package com.kai.bill.domain.repository

import com.kai.bill.domain.model.Budget
import kotlinx.coroutines.flow.Flow

/**
 * 预算仓储。
 *
 * 预算是「一个周期一条」的配置型数据，因此写入统一用 `upsert` 而非 insert：
 * 用户反复调整预算金额时不应该产生多条记录。
 */
interface BudgetRepository {

    /**
     * 观察全部预算（含分类预算与已停用的）。
     *
     * @return 预算流
     */
    fun observeAll(): Flow<List<Budget>>

    /**
     * 观察总额预算。
     *
     * 约定：[Budget.categoryId] 为 null 即总额预算。将来支持分类预算时，
     * 总额与分类预算靠这个字段区分，不会混淆。
     *
     * @return 预算流；**用户从未设置过预算时发射 null**，首页据此不渲染预算卡
     */
    fun observeTotalBudget(): Flow<Budget?>

    /**
     * 观察某个分类的预算。
     *
     * @param categoryId 分类主键
     * @return 该分类的预算流；未单独设置时发射 null
     */
    fun observeByCategory(categoryId: Long): Flow<Budget?>

    /**
     * 观察已启用的预算，用于超支检查。
     *
     * @return 已启用预算的流
     */
    fun observeEnabled(): Flow<List<Budget>>

    /**
     * 新增或更新一条预算；以主键匹配。
     *
     * @param budget 待保存预算；[Budget.id] 为 0 时新增，否则更新
     */
    suspend fun upsert(budget: Budget)

    /**
     * 按主键删除一条预算。
     *
     * @param id 预算主键
     */
    suspend fun deleteById(id: Long)
}
