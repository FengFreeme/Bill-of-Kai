package com.kai.bill.data.mapper

import com.kai.bill.core.db.entity.BillType as EntityBillType
import com.kai.bill.domain.model.BillType

/** Domain ↔ Entity [BillType] 转换；同包唯一入口，避免各 Mapper 重复定义冲突。 */
internal fun BillType.toEntity(): EntityBillType = when (this) {
    BillType.EXPENSE -> EntityBillType.EXPENSE
    BillType.INCOME -> EntityBillType.INCOME
    BillType.TRANSFER -> EntityBillType.TRANSFER
}

internal fun EntityBillType.toDomain(): BillType = when (this) {
    EntityBillType.EXPENSE -> BillType.EXPENSE
    EntityBillType.INCOME -> BillType.INCOME
    EntityBillType.TRANSFER -> BillType.TRANSFER
}
