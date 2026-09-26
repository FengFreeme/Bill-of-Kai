package com.kai.bill.data.mapper

import com.kai.bill.core.db.entity.BillEntity
import com.kai.bill.domain.model.Bill

/**
 * Bill Entity ⇄ Domain 映射。
 *
 * Entity 字段名 `time` 对应领域 `tradeTimeMillis`。
 * 枚举转换在 [toEntity] / [toDomain] 两个同包入口里，本文件不再自带私有实现。
 */
object BillMapper {

    fun toDomain(entity: BillEntity): Bill = Bill(
        id = entity.id,
        amountCents = entity.amountCents,
        type = entity.type.toDomain(),
        countInStats = entity.countInStats,
        isRefund = entity.isRefund,
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
        isRefund = bill.isRefund,
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
