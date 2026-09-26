package com.kai.bill.domain.usecase.bill

import com.kai.bill.domain.model.Bill
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.RefundCategory
import com.kai.bill.domain.model.SourceType
import com.kai.bill.domain.repository.BillRepository
import com.kai.bill.domain.time.Clock
import java.util.UUID
import javax.inject.Inject

/**
 * 手动记一笔 → Repository 写入。
 *
 * 与 data 层 `BillIngestor` 的边界：本 UseCase 只处理已规范化的手动账单；
 * 通知/短信的解析去重落库走 Ingestor，不经过此处。
 *
 * 时间语义：记录时间（createdAt/updatedAt）与交易时间（tradeTimeMillis）是两个概念，
 * 用户可能在 3 号补记 1 号的消费，统计必须按交易时间归月。
 * [Params.tradeTimeMillis] 为空时用当前时钟；非空则尊重调用方（补记场景）。
 *
 * 手动账统一 [SourceType.MANUAL]，并生成唯一 [Bill.dedupHash]
 * （空串会撞唯一索引，导致第二笔起静默失败）。
 */
class RecordBillUseCase @Inject constructor(
    private val billRepository: BillRepository,
    private val clock: Clock
) {

    /**
     * 记一笔入参：只收用户可变字段，落库字段由本 UseCase 补齐。
     *
     * @property amountCents 金额，单位「分」，必须 > 0
     * @property type 账单类型
     * @property categoryId 分类 ID，必须非 0
     * @property accountId 账户 ID，可空
     * @property note 备注，空白视为无
     * @property tradeTimeMillis 交易时间；null 表示「现在」
     */
    data class Params(
        val amountCents: Long,
        val type: BillType,
        val categoryId: Long,
        val accountId: Long?,
        val note: String?,
        val countInStats: Boolean = true,
        val tradeTimeMillis: Long? = null
    )

    /**
     * 组装并保存一笔手动账单。
     *
     * @return 新账单主键；Room IGNORE 冲突时可能返回 -1
     * @throws IllegalArgumentException 金额/分类/交易时间不合法
     */
    suspend operator fun invoke(params: Params): Long {
        require(params.amountCents > 0L) { "金额必须大于 0" }
        require(params.categoryId != 0L) { "必须选择分类" }
        val now = clock.nowMillis()
        val tradeTime = params.tradeTimeMillis ?: now
        require(tradeTime > 0L) { "交易时间无效" }
        val bill = Bill(
            id = 0L,
            amountCents = params.amountCents,
            type = params.type,
            countInStats = params.countInStats,
            // 手动退款同样要冲抵支出：判据与解析侧一致（收入 + 退款分类），不额外加开关。
            // 代价是拿不到原消费归属，冲抵落在当月与本分类。
            isRefund = params.type == BillType.INCOME && params.categoryId == RefundCategory.ID,
            categoryId = params.categoryId,
            accountId = params.accountId,
            merchant = null,
            note = params.note?.takeIf { it.isNotBlank() },
            tradeTimeMillis = tradeTime,
            source = SourceType.MANUAL,
            rawText = null,
            // 手动账必须唯一：空串会撞唯一索引，导致第二笔起静默失败
            dedupHash = "manual|${UUID.randomUUID()}",
            createdAt = now,
            updatedAt = now
        )
        return billRepository.save(bill)
    }
}
