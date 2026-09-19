package com.kai.bill.domain.repository

import com.kai.bill.domain.model.Bill
import com.kai.bill.domain.model.BillFilter
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.DateRange
import kotlinx.coroutines.flow.Flow

/**
 * 账单仓储 —— **云同步的替换点**。
 *
 * 本项目当前实现为 `BillRepositoryImpl`（Room）。将来若要加云同步，
 * 只需再写一个 `CloudBillRepository` 并在 `app/di/RepositoryModule.kt` 换一行绑定，
 * 上层 UseCase 与 UI **零改动** —— 这正是本接口存在的全部意义。
 *
 * 约定：
 * - 返回 [Flow] 的方法以 `observe` 开头，表示数据变更时会自动重新发射
 * - 一次性读取为 `suspend`，以 `get` 开头
 * - 金额一律 `Long`（分），时间一律 `Long`（毫秒）
 */
interface BillRepository {

    /**
     * 观察指定时间范围内、满足筛选条件的账单列表。
     *
     * 返回的是**热流**：数据库发生任何变更时会自动重新发射，UI 层无需手动刷新。
     * 生命周期由调用方（ViewModel）的协程作用域管理。
     *
     * @param range 时间范围；**按交易时间 [Bill.tradeTimeMillis] 过滤**，不是记录时间
     * @param filter 组合筛选条件（类型 / 分类 / 账户 / 是否计入统计）；
     *               为 null 表示只按时间范围过滤
     * @return 按交易时间**倒序**（最新在前）排列的账单流
     */
    fun observeBills(range: DateRange, filter: BillFilter?): Flow<List<Bill>>

    /**
     * 观察单条账单；用于编辑页在数据被其它来源（通知解析等）改动后自动刷新。
     *
     * @param id 账单主键
     * @return 账单流；记录被删除后发射 null
     */
    fun observeById(id: Long): Flow<Bill?>

    /**
     * 一次性读取单条账单。
     *
     * @param id 账单主键
     * @return 对应账单；不存在返回 null
     */
    suspend fun getById(id: Long): Bill?

    /**
     * 保存一笔账单（新增或更新）。
     *
     * @param bill 待保存账单；[Bill.id] 为 0 时新增，否则更新
     * @return 保存后的记录 ID；新增时返回数据库自增 ID
     */
    suspend fun save(bill: Bill): Long

    /**
     * 时间窗内是否已存在「金额 + 类型」相同的账单。
     *
     * 这是与 `dedupHash` 唯一索引互补的**第二道去重**：唯一索引只能识别落在同一个「秒格」里的
     * 重复，而两条投递的实际间隔只要跨过秒格边界就会漏网（见 `DedupKey`）。本方法按实际时间差
     * 判断，与秒格对齐无关，把「相近时间内的同一笔」从概率判断变成确定判断。
     *
     * @param amountCents 金额（分）
     * @param type 账单类型
     * @param timeMillis 待判定账单的交易时间（毫秒）
     * @param windowMillis 窗口半径；匹配区间为 `timeMillis ± windowMillis`
     * @return 命中即 true
     */
    suspend fun existsInWindow(
        amountCents: Long,
        type: BillType,
        timeMillis: Long,
        windowMillis: Long
    ): Boolean

    /**
     * 查询时间窗内、来源为自动采集的账单 —— 信号决策用它回答「这笔记过没有」与「哪些能补分类」。
     *
     * 本方法存在的意义是让「哪些账单允许被程序改写」这条规则留在实现层，
     * domain 只表达「我需要一批候选」这一意图，SQL 细节不外泄。
     *
     * 实现约定：结果**只包含自动采集来源**（排除 [com.kai.bill.domain.model.SourceType.MANUAL]），
     * 且**不按分类过滤** —— 「分类仍是兜底值」那条筛选留给调用方，
     * 因为调用方还要用这批数据判断「这笔是否已经记过」，而**已记过的那笔往往已带正确分类**。
     *
     * @param startMillis 窗口下界（含）
     * @param endMillis 窗口上界（含）
     * @return 按交易时间倒序的账单；可能为空
     */
    suspend fun findAutoBillsInWindow(startMillis: Long, endMillis: Long): List<Bill>

    /**
     * 按主键删除一笔账单。
     *
     * @param id 账单主键
     */
    suspend fun deleteById(id: Long)
}
