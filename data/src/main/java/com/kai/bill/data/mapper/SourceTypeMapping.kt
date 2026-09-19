package com.kai.bill.data.mapper

import com.kai.bill.core.db.entity.SourceType as EntitySourceType
import com.kai.bill.domain.model.SourceType

/**
 * Domain ↔ Entity [SourceType] 转换；同包唯一入口。
 *
 * 与 [BillTypeMapping] 同样的理由：「账单」和「待确认账单」两张表都要这个转换，
 * 各写一份私有实现迟早会出现「一边加了新来源、另一边忘了加」的漏改 ——
 * 而这种漏改的后果是插入时抛异常，只会在真机跑单时才暴露。
 *
 * ⚠️ `Converters.toSourceType` 解析失败会**回退 `MANUAL`**（不抛异常），
 * 所以这里漏一个分支不会报错，只会让账目来源静默变错 —— 是本文件最需要小心的地方。
 */
internal fun SourceType.toEntity(): EntitySourceType = when (this) {
    SourceType.NOTIFICATION -> EntitySourceType.NOTIFICATION
    SourceType.SMS -> EntitySourceType.SMS
    SourceType.MANUAL -> EntitySourceType.MANUAL
    SourceType.ACCESSIBILITY -> EntitySourceType.ACCESSIBILITY
    SourceType.SCREENSHOT -> EntitySourceType.SCREENSHOT
}

internal fun EntitySourceType.toDomain(): SourceType = when (this) {
    EntitySourceType.NOTIFICATION -> SourceType.NOTIFICATION
    EntitySourceType.SMS -> SourceType.SMS
    EntitySourceType.MANUAL -> SourceType.MANUAL
    EntitySourceType.ACCESSIBILITY -> SourceType.ACCESSIBILITY
    EntitySourceType.SCREENSHOT -> SourceType.SCREENSHOT
}
