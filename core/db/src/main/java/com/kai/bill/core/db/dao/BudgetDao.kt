package com.kai.bill.core.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.kai.bill.core.db.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

/**
 * 预算表的数据访问对象。
 *
 * 写入统一用 `@Upsert`：预算是「一个周期一条」的配置型数据，
 * 用 REPLACE 语义会导致自增主键被重置，破坏 [BudgetEntity.id] 的稳定性。
 */
@Dao
interface BudgetDao {

    /** 观察全部预算（含分类预算与未启用的） */
    @Query("SELECT * FROM budget ORDER BY id ASC")
    fun observeAll(): Flow<List<BudgetEntity>>

    /**
     * 观察总额预算。
     *
     * `categoryId IS NULL` 即总额预算，这是框架文档定下的约定，
     * 将来加分类预算时无需改表，也不会与总额预算混淆。
     *
     * @return 预算流；用户从未设置过预算时发射 null（首页据此不渲染预算卡）
     */
    @Query("SELECT * FROM budget WHERE categoryId IS NULL LIMIT 1")
    fun observeTotalBudget(): Flow<BudgetEntity?>

    /** 观察某个分类的预算；未设置时发射 null */
    @Query("SELECT * FROM budget WHERE categoryId = :categoryId LIMIT 1")
    fun observeByCategory(categoryId: Long): Flow<BudgetEntity?>

    /** 观察已启用的预算，用于预算超支检查 */
    @Query("SELECT * FROM budget WHERE enabled = 1 ORDER BY id ASC")
    fun observeEnabled(): Flow<List<BudgetEntity>>

    @Upsert
    suspend fun upsert(budget: BudgetEntity)

    @Update
    suspend fun update(budget: BudgetEntity)

    @Query("DELETE FROM budget WHERE id = :id")
    suspend fun deleteById(id: Long)
}
