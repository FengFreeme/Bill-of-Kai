package com.kai.bill.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kai.bill.core.db.entity.BillType
import com.kai.bill.core.db.entity.PendingBillEntity
import kotlinx.coroutines.flow.Flow

/**
 * 待确认表的数据访问对象。
 *
 * 约定与 [BillDao] 一致：返回 `Flow` 的方法以 `observe` 开头；一次性读取 / 写入为 `suspend`。
 *
 * 唯一索引在 `dedupHash` 上，因此 [insert] 用 `IGNORE`：同一笔通知重复入队时静默失败并返回 -1，
 * 调用方（流水线）据此判断「已在列表里」，不再重复发通知。
 */
@Dao
interface PendingBillDao {

    /**
     * 观察全部待确认记录。
     *
     * 按 [PendingBillEntity.createdAt] **倒序**：待确认列表是「新来的在最上面」，
     * 与账单列表按交易时间倒序是同一套心智。
     */
    @Query("SELECT * FROM pending_bill ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PendingBillEntity>>

    /**
     * 观察待确认条数，用于首页卡片与通知角标。
     *
     * 单独一条 `COUNT(*)` 而不是让 UI 拿列表算长度：首页只需要一个数字，
     * 拉全表（含原文）纯属浪费。
     */
    @Query("SELECT COUNT(*) FROM pending_bill")
    fun observeCount(): Flow<Int>

    /** 一次性读取全部待确认记录，用于「全部接受建议」的批量处理 */
    @Query("SELECT * FROM pending_bill ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingBillEntity>

    /** 一次性读取单条，供「改一下」取原文与建议值 */
    @Query("SELECT * FROM pending_bill WHERE id = :id")
    suspend fun getById(id: Long): PendingBillEntity?

    /**
     * 入队一条待确认记录。
     *
     * @return 新记录行 ID；**返回 -1 表示该 dedupHash 已在列表里**（同一笔通知重复投递）
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bill: PendingBillEntity): Long

    /**
     * 时间窗内是否已存在「金额 + 建议方向」相同的待确认记录。
     *
     * 与 `dedupHash` 唯一索引互补的第二道去重（见 `DedupKey.MATCH_WINDOW_MILLIS`）：
     * 唯一索引只认「同一秒格」，跨过秒格边界的重发会漏网，这里按实际时间差兜住。
     *
     * `suggestedType` 用 `IS` 而非 `=`：方向判不出的记录该字段为 null，
     * 而 `= NULL` 在 SQL 里恒为假，会让「两条都没方向」互相看不见。
     *
     * @param amountCents 金额（分）
     * @param suggestedType 建议方向；可为 null（判不出方向）
     * @param startMillis 窗口下界（含）
     * @param endMillis 窗口上界（含）
     * @return 命中任意一条即 true
     */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM pending_bill
            WHERE amountCents = :amountCents
              AND suggestedType IS :suggestedType
              AND time BETWEEN :startMillis AND :endMillis
        )
        """
    )
    suspend fun existsInWindow(
        amountCents: Long,
        suggestedType: BillType?,
        startMillis: Long,
        endMillis: Long
    ): Boolean

    /** 按主键删除（用户「接受建议」或「不要这笔」之后调用） */
    @Query("DELETE FROM pending_bill WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 批量删除，配合「全部接受建议」用。
     *
     * 逐条删在批量场景下会退化成 N 次事务，因此这里走一条 `IN`。
     *
     * @param ids 待删除的主键；传空列表时 SQL 的 `IN ()` 非法，由调用方保证非空
     */
    @Query("DELETE FROM pending_bill WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /**
     * 清理入队超过保留期的记录。
     *
     * 待确认是「当时没判准、等用户拍板」的临时数据，长期堆着只会让列表失去意义；
     * 用户真想要这笔账，还有通知原文和手动记账两条路。
     *
     * @param cutoffMillis 时间水位线：入队时间早于它的记录会被删除
     * @return 删除条数
     */
    @Query("DELETE FROM pending_bill WHERE createdAt < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int
}
