package com.kai.bill.data.mapper

import com.kai.bill.core.db.entity.PendingBillEntity
import com.kai.bill.core.db.entity.PendingReason as EntityPendingReason
import com.kai.bill.domain.model.PendingBill
import com.kai.bill.domain.model.PendingReason

/**
 * PendingBill Entity ⇄ Domain 映射。
 *
 * Entity 字段名 `time` 对应领域 `tradeTimeMillis`（与 [BillMapper] 同一约定）。
 * 枚举转换：[com.kai.bill.domain.model.BillType] 与 [com.kai.bill.domain.model.SourceType]
 * 复用同包的公共入口，只有 `PendingReason` 是本文件私有的（仅此一处用到）。
 */
object PendingBillMapper {

    fun toDomain(entity: PendingBillEntity): PendingBill = PendingBill(
        id = entity.id,
        amountCents = entity.amountCents,
        suggestedType = entity.suggestedType?.toDomain(),
        suggestedCategoryId = entity.suggestedCategoryId,
        suggestedAccountId = entity.suggestedAccountId,
        suggestedCountInStats = entity.suggestedCountInStats,
        reason = entity.reason.toDomain(),
        matchedKeyword = entity.matchedKeyword,
        tradeTimeMillis = entity.time,
        source = entity.source.toDomain(),
        rawText = entity.rawText,
        dedupHash = entity.dedupHash,
        createdAt = entity.createdAt
    )

    fun toEntity(bill: PendingBill): PendingBillEntity = PendingBillEntity(
        id = bill.id,
        amountCents = bill.amountCents,
        suggestedType = bill.suggestedType?.toEntity(),
        suggestedCategoryId = bill.suggestedCategoryId,
        suggestedAccountId = bill.suggestedAccountId,
        suggestedCountInStats = bill.suggestedCountInStats,
        reason = bill.reason.toEntity(),
        matchedKeyword = bill.matchedKeyword,
        time = bill.tradeTimeMillis,
        source = bill.source.toEntity(),
        rawText = bill.rawText,
        dedupHash = bill.dedupHash,
        createdAt = bill.createdAt
    )
}

private fun EntityPendingReason.toDomain(): PendingReason = when (this) {
    EntityPendingReason.DIRECTION_UNKNOWN -> PendingReason.DIRECTION_UNKNOWN
    EntityPendingReason.SELF_TRANSFER -> PendingReason.SELF_TRANSFER
    EntityPendingReason.GIFT -> PendingReason.GIFT
    EntityPendingReason.GENERIC_INBOUND -> PendingReason.GENERIC_INBOUND
}

private fun PendingReason.toEntity(): EntityPendingReason = when (this) {
    PendingReason.DIRECTION_UNKNOWN -> EntityPendingReason.DIRECTION_UNKNOWN
    PendingReason.SELF_TRANSFER -> EntityPendingReason.SELF_TRANSFER
    PendingReason.GIFT -> EntityPendingReason.GIFT
    PendingReason.GENERIC_INBOUND -> EntityPendingReason.GENERIC_INBOUND
}
