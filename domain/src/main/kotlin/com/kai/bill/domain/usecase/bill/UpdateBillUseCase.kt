package com.kai.bill.domain.usecase.bill

import com.kai.bill.domain.model.Bill
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.repository.BillRepository
import com.kai.bill.domain.time.Clock
import javax.inject.Inject

/**
 * 更新已有账单。
 *
 * 以 [Params.snapshot] 为不可变字段真源（dedupHash / createdAt /
 * merchant / source / rawText），只覆盖用户在编辑页改过的字段。
 * [countInStats] 与 [Params.tradeTimeMillis] 也是编辑页可改字段：
 * 交易时间为空时保留快照原时间，避免改备注把归月交易时间冲掉。
 */
class UpdateBillUseCase @Inject constructor(
    private val billRepository: BillRepository,
    private val clock: Clock
) {

    /**
     * 编辑入参。
     *
     * @property snapshot 编辑前原账单（必须 id != 0）
     * @property amountCents 新金额，单位「分」
     * @property type 新类型
     * @property categoryId 新分类
     * @property accountId 新账户，可空
     * @property note 新备注，空白视为无
     * @property countInStats 是否计入统计与预算（编辑页可改）
     * @property tradeTimeMillis 新交易时间；null 表示保留快照原交易时间
     */
    data class Params(
        val snapshot: Bill,
        val amountCents: Long,
        val type: BillType,
        val categoryId: Long,
        val accountId: Long?,
        val note: String?,
        val countInStats: Boolean = true,
        val tradeTimeMillis: Long? = null
    )

    /**
     * 基于快照合并可变字段并保存。
     *
     * @return 账单主键
     * @throws IllegalArgumentException 快照 id 为 0、金额/分类/交易时间非法
     */
    suspend operator fun invoke(params: Params): Long {
        require(params.snapshot.id != 0L) { "更新账单要求 id != 0" }
        require(params.amountCents > 0L) { "金额必须大于 0" }
        require(params.categoryId != 0L) { "必须选择分类" }
        val tradeTime = params.tradeTimeMillis ?: params.snapshot.tradeTimeMillis
        require(tradeTime > 0L) { "交易时间无效" }
        val updated = params.snapshot.copy(
            amountCents = params.amountCents,
            type = params.type,
            countInStats = params.countInStats,
            categoryId = params.categoryId,
            accountId = params.accountId,
            note = params.note?.takeIf { it.isNotBlank() },
            tradeTimeMillis = tradeTime,
            updatedAt = clock.nowMillis()
        )
        return billRepository.save(updated)
    }
}
