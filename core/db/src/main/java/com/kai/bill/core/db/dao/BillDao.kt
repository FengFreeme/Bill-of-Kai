package com.kai.bill.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kai.bill.core.db.entity.BillEntity
import com.kai.bill.core.db.entity.BillType
import com.kai.bill.core.db.entity.SourceType
import kotlinx.coroutines.flow.Flow

/**
 * 账单表的数据访问对象。
 *
 * 约定：返回 `Flow` 的方法以 `observe` 开头（数据库变更时自动重发，UI 无需手动刷新）、
 * 一次性查询为 `suspend` 且以 `get` / `find` 开头、列表一律按 [BillEntity.time] 倒序。
 *
 * 列表查询只保留 [observeAll] / [observeInRange] / [observeFiltered] 三个：筛选有 4 个维度
 * （类型 / 分类 / 账户 / 是否计入统计），每种组合各写一条 SQL 要 16 个方法且必然重复，
 * 因此统一收口到 [observeFiltered] 的「传 null 即不筛选」。
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
     * `:param IS NULL OR col = :param` 是「传 null 即跳过该维度」的标准写法：SQLite 遇到
     * `:param IS NULL` 会短路、不再比较第二项，因此四个维度全传 null 与只查时间范围开销几乎一致。
     *
     * @param startMillis 起始毫秒（含）；endMillis 结束毫秒（含）
     * @param type 账单类型；null 表示不过滤。**按「统计维度」而不是字面 `type` 过滤**：传 `EXPENSE`
     *             时退款行（`type = 'INCOME'`）也会被带出来（它是支出侧的负项），传 `INCOME` 时
     *             退款行不出现。与 `StatsDao` 同一套判据
     * @param countInStats 是否计入统计；null 表示不过滤。传 true 与 false 是两种意图：
     *                     前者「只看计入统计的」，后者「只看转账还款」
     */
    @Query(
        """
        SELECT * FROM bill
        WHERE time BETWEEN :startMillis AND :endMillis
          AND (:type IS NULL OR CASE WHEN isRefund = 1 THEN 'EXPENSE' ELSE type END = :type)
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
     * 时间窗内是否已存在「金额 + 类型」相同的账单。
     *
     * 与 `dedupHash` 唯一索引互补的第二道去重：唯一索引只认「同一秒格」，跨格（如 `x.999` 与
     * `(x+1).000`）就会漏网；本查询按实际时间差判断，与秒格对齐无关（见 `DedupKey.MATCH_WINDOW_MILLIS`）。
     * 窄范围走 `time` 索引，窗口只有秒级，开销可忽略。
     *
     * @param amountCents 金额（分）
     * @param startMillis 窗口下界（含）；endMillis 上界（含）
     */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM bill
            WHERE amountCents = :amountCents
              AND type = :type
              AND time BETWEEN :startMillis AND :endMillis
        )
        """
    )
    suspend fun existsInWindow(
        amountCents: Long,
        type: BillType,
        startMillis: Long,
        endMillis: Long
    ): Boolean

    /**
     * 更新一条账单。以主键匹配，要求调用方传入的实体 id 非 0。
     */
    @Update
    suspend fun update(bill: BillEntity)

    /**
     * 查时间窗内、来源为自动采集的账单 —— 信号决策的唯一查询入口。
     *
     * **刻意不按分类过滤**，因为它要同时回答两个问题：① 这笔是不是已经记过了（窗口内只要有
     * **同金额**的账单就算记过，与分类无关）；② 哪些还允许被补分类（由调用方在内存里筛
     * 「分类仍是兜底值」的那些）。
     *
     * NOTE: ① **绝不能退化成「只看兜底分类」** —— 真机已复现：信号常比通知早约 0.5s 到达，
     * 通知随后落库时 S3 会从信号窗口读到这条信号，那笔通知账单**一落库就带着正确分类**（不在
     * 兜底集合里）；只认兜底分类就「看不见」它，会误判成「无账可配」而把同一笔记两次。
     *
     * `time BETWEEN` 走 `time` 索引（窗口由调用方保证为分钟级）；`source IN` 排除手动记账 ——
     * 用户主动输入的分类不该被自动覆盖。
     *
     * @param startMillis 窗口下界（含）；endMillis 上界（含）
     * @param sources 允许被自动判定的来源（**不含 MANUAL**）
     */
    @Query(
        """
        SELECT * FROM bill
        WHERE time BETWEEN :startMillis AND :endMillis
          AND source IN (:sources)
        ORDER BY time DESC
        """
    )
    suspend fun findAutoBillsInWindow(
        startMillis: Long,
        endMillis: Long,
        sources: List<SourceType>
    ): List<BillEntity>

    /**
     * 回溯「这笔退款冲抵的是哪笔支出」—— 取窗口内**最近的一笔同金额支出**。
     *
     * 只能靠启发式：采集链路不解析商户（`merchant` 恒为 null）、也没有结构化的订单号。
     * 窗口由调用方卡死（见 `BillRepositoryImpl`）；匹配不到不是错误，退款退回自身归属。
     *
     * @param startMillis 窗口下界（含）
     * @param endMillis 窗口上界（含）；传退款发生时间，只回溯早于它的支出
     */
    @Query(
        """
        SELECT * FROM bill
        WHERE amountCents = :amountCents
          AND type = 'EXPENSE'
          AND isRefund = 0
          AND time BETWEEN :startMillis AND :endMillis
        ORDER BY time DESC
        LIMIT 1
        """
    )
    suspend fun findRefundTarget(
        amountCents: Long,
        startMillis: Long,
        endMillis: Long
    ): BillEntity?

    /**
     * 按主键删除一条账单。
     */
    @Query("DELETE FROM bill WHERE id = :id")
    suspend fun deleteById(id: Long)
}
