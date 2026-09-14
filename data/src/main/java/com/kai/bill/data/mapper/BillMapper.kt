package com.kai.bill.data.mapper

import com.kai.bill.core.db.entity.BillEntity
import com.kai.bill.core.db.entity.SourceType as EntitySourceType
import com.kai.bill.domain.model.Bill
import com.kai.bill.domain.model.SourceType

/**
 * Bill Entity ⇄ Domain 映射。
 *
 * Entity 字段名 `time` 对应领域 `tradeTimeMillis`。
 */
object BillMapper {

    fun toDomain(entity: BillEntity): Bill = Bill(
        id = entity.id,
        amountCents = entity.amountCents,
        type = entity.type.toDomain(),
        countInStats = entity.countInStats,
        categoryId = entity.categoryId,
        accountId = entity.accountId,
        merchant = entity.merchant,
        note = entity.note,
        tradeTimeMillis = entity.time,
        source = entity.source.toDomain(),
        rawText = entity.rawText,
        dedupHash = entity.dedupHash,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt
    )

    fun toEntity(bill: Bill): BillEntity = BillEntity(
        id = bill.id,
        amountCents = bill.amountCents,
        type = bill.type.toEntity(),
        countInStats = bill.countInStats,
        categoryId = bill.categoryId,
        accountId = bill.accountId,
        merchant = bill.merchant,
        note = bill.note,
        time = bill.tradeTimeMillis,
        source = bill.source.toEntity(),
        rawText = bill.rawText,
        dedupHash = bill.dedupHash,
        createdAt = bill.createdAt,
        updatedAt = bill.updatedAt
    )
}

private fun EntitySourceType.toDomain(): SourceType = when (this) {
    EntitySourceType.NOTIFICATION -> SourceType.NOTIFICATION
    EntitySourceType.SMS -> SourceType.SMS
    EntitySourceType.MANUAL -> SourceType.MANUAL
}

private fun SourceType.toEntity(): EntitySourceType = when (this) {
    SourceType.NOTIFICATION -> EntitySourceType.NOTIFICATION
    SourceType.SMS -> EntitySourceType.SMS
    SourceType.MANUAL -> EntitySourceType.MANUAL
}
