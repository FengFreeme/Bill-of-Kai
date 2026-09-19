package com.kai.bill.domain.repository

import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.PendingBill
import kotlinx.coroutines.flow.Flow

/**
 * 待确认账单仓储。
 *
 * 存在的意义：解析链路能判准的已经直接落账了，判不准的必须有地方先存下来、
 * 再由用户一次性拍板。没有这一层，「判不准」只能二选一 —— 要么错记（污染统计），
 * 要么丢弃（用户永远不知道漏了钱）。
 *
 * 约定与其它仓储一致：`observe` 开头返回 [Flow]，一次性操作为 `suspend`。
 */
interface PendingBillRepository {

    /**
     * 观察全部待确认记录，按入队时间**倒序**（最新的在最前）。
     */
    fun observeAll(): Flow<List<PendingBill>>

    /**
     * 观察待确认条数，供首页卡片与通知角标使用。
     *
     * 单独开一个方法而不是让调用方拿列表算长度：首页只需要数字，不必拉全表原文。
     */
    fun observeCount(): Flow<Int>

    /**
     * 入队一条待确认记录。
     *
     * @param bill 待确认记录；[PendingBill.id] 由底层生成
     * @return 新记录 ID；**返回 -1 表示同一笔（dedupHash 相同）已在列表中**，
     *         调用方据此避免重复提示
     */
    suspend fun enqueue(bill: PendingBill): Long

    /**
     * 时间窗内是否已存在「金额 + 建议方向」相同的待确认记录。
     *
     * 与 `dedupHash` 唯一索引互补的第二道去重（同 [BillRepository.existsInWindow]）：
     * 唯一索引只认「同一秒格」，跨过秒格边界的重发会漏网，这里按实际时间差兜住。
     *
     * @param amountCents 金额（分）
     * @param suggestedType 建议方向；判不出方向时传 null
     * @param timeMillis 待判定记录的交易时间（毫秒）
     * @param windowMillis 窗口半径；匹配区间为 `timeMillis ± windowMillis`
     * @return 命中即 true
     */
    suspend fun existsInWindow(
        amountCents: Long,
        suggestedType: BillType?,
        timeMillis: Long,
        windowMillis: Long
    ): Boolean

    /**
     * 一次性读取全部记录，按入队时间**正序**（配合「全部接受建议」按先后顺序落库）。
     */
    suspend fun getAll(): List<PendingBill>

    /**
     * 一次性读取单条；已删除返回 null。
     */
    suspend fun getById(id: Long): PendingBill?

    /**
     * 删除一条（用户「接受建议」落库后，或选择「不要这笔」时调用）。
     */
    suspend fun deleteById(id: Long)

    /**
     * 批量删除（「全部接受建议」落库后调用）。
     */
    suspend fun deleteByIds(ids: List<Long>)

    /**
     * 清理入队超过保留期的记录。
     *
     * @param cutoffMillis 时间水位线；入队时间早于它的记录会被删除
     * @return 删除条数
     */
    suspend fun deleteOlderThan(cutoffMillis: Long): Int
}
