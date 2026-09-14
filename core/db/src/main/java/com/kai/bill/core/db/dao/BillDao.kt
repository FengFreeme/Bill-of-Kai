package com.kai.bill.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kai.bill.core.db.entity.BillEntity
import com.kai.bill.core.db.entity.BillType
import kotlinx.coroutines.flow.Flow

/**
 * 账单表的数据访问对象。
 *
 * 约定：
 * - 返回 `Flow` 的方法以 `observe` 开头，数据库变更时自动重新发射，UI 无需手动刷新
 * - 一次性查询为 `suspend`，以 `get` / `find` 开头
 * - 所有列表按 [BillEntity.time]（交易时间）**倒序**，最新的在最前
 *
 * 只保留 [observeAll]、[observeInRange]、[observeFiltered] 三个列表查询：
 * 账单筛选有 4 个维度（类型 / 分类 / 账户 / 是否计入统计），
 * 若为每种组合各写一条 SQL 需要 16 个方法且必然互相重复，
 * 因此统一收口到 [observeFiltered] 的「传 null 即不筛选」写法。
 */
@Dao
interface BillDao {

    /**
     * 观察全部账单。
     *
     * @return 按交易时间倒序排列的账单流
     */
    @Query("SELECT * FROM bill ORDER BY time DESC")
    fun observeAll(): Flow<List<BillEntity>>

    /**
     * 观察指定时间范围内的账单，不附加其它筛选。
     *
     * 这是最高频的调用（首页本月流水），单独保留一个简洁签名以避免每次都传四个 null。
     *
     * @param startMillis 起始毫秒（含）
     * @param endMillis 结束毫秒（含）
     * @return 按交易时间倒序排列的账单流
     */
    @Query(
        """
        SELECT * FROM bill
        WHERE time BETWEEN :startMillis AND :endMillis
        ORDER BY time DESC
        """
    )
    fun observeInRange(startMillis: Long, endMillis: Long): Flow<List<BillEntity>>

    /**
     * 观察指定时间范围内、按条件组合筛选的账单。
     *
     * `:param IS NULL OR col = :param` 是「传 null 即跳过该维度」的标准写法：
     * SQLite 遇到 `:param IS NULL` 成立时会短路，不会再去比较第二项，
     * 因此四个维度全传 null 与只查时间范围的开销几乎一致。
     *
     * @param startMillis 起始毫秒（含）
     * @param endMillis 结束毫秒（含）
     * @param type 账单类型；null 表示不过滤类型
     * @param categoryId 分类 ID；null 表示不过滤分类
     * @param accountId 账户 ID；null 表示不过滤账户
     * @param countInStats 是否计入统计；null 表示不过滤。
     *                     注意传 true 与传 false 是两种不同意图：前者「只看计入统计的」，
     *                     后者「只看转账还款」
     * @return 按交易时间倒序排列的账单流
     */
    @Query(
        """
        SELECT * FROM bill
        WHERE time BETWEEN :startMillis AND :endMillis
          AND (:type IS NULL OR type = :type)
          AND (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:accountId IS NULL OR accountId = :accountId)
          AND (:countInStats IS NULL OR countInStats = :countInStats)
        ORDER BY time DESC
        """
    )
    fun observeFiltered(
        startMillis: Long,
        endMillis: Long,
        type: BillType?,
        categoryId: Long?,
        accountId: Long?,
        countInStats: Boolean?
    ): Flow<List<BillEntity>>

    /**
     * 观察单条账单；用于编辑页在数据被其它来源更新后自动刷新。
     *
     * @return 账单流；记录被删除后发射 null
     */
    @Query("SELECT * FROM bill WHERE id = :id")
    fun observeById(id: Long): Flow<BillEntity?>

    /**
     * 一次性读取单条账单。
     */
    @Query("SELECT * FROM bill WHERE id = :id")
    suspend fun getById(id: Long): BillEntity?

    /**
     * 时间窗模糊判重（去重的第二道防线）。
     *
     * 同一笔消费的通知与短信往往相差几秒到达，金额与归一化商户相同，
     * 仅靠 `dedupHash` 的分钟级时间可能无法命中，因此再按时间窗捞一次。
     *
     * NOTE: `ifnull(merchant, '')` 而非 `merchant = :merchant` ——
     *       SQLite 中 `NULL = NULL` 判定为假，直接比较会让「无商户」的账单永远查不到重复。
     *
     * @param amountCents 金额，单位「分」
     * @param merchant 归一化后的商户名，可为 null
     * @param tradeTimeMillis 交易发生时间（毫秒）
     * @param windowMillis 时间窗长度（毫秒），建议 60_000
     * @return 命中的重复账单；无重复返回 null
     */
    @Query(
        """
        SELECT * FROM bill
        WHERE amountCents = :amountCents
          AND ifnull(merchant, '') = ifnull(:merchant, '')
          AND ABS(time - :tradeTimeMillis) <= :windowMillis
        LIMIT 1
        """
    )
    suspend fun findDuplicate(
        amountCents: Long,
        merchant: String?,
        tradeTimeMillis: Long,
        windowMillis: Long
    ): BillEntity?

    /**
     * 插入一条账单。
     *
     * 使用 `IGNORE` 而非 `ABORT`：`dedupHash` 上有唯一索引，重复插入时会静默失败并返回 -1，
     * 这样「数据库层防重复」对调用方是无感的，不必到处写 try-catch。
     *
     * @return 新记录的行 ID；**返回 -1 表示与既有记录重复，未插入**
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bill: BillEntity): Long

    /**
     * 更新一条账单。以主键匹配，要求调用方传入的实体 id 非 0。
     */
    @Update
    suspend fun update(bill: BillEntity)

    /**
     * 按主键删除一条账单。
     */
    @Query("DELETE FROM bill WHERE id = :id")
    suspend fun deleteById(id: Long)
}
