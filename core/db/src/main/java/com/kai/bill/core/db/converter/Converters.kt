package com.kai.bill.core.db.converter

import androidx.room.TypeConverter
import com.kai.bill.core.db.entity.AccountType
import com.kai.bill.core.db.entity.BillType
import com.kai.bill.core.db.entity.BudgetPeriod
import com.kai.bill.core.db.entity.DailyMode
import com.kai.bill.core.db.entity.PendingReason
import com.kai.bill.core.db.entity.SourceType

/**
 * Room 类型转换器：枚举 ⇄ TEXT。
 *
 * 时间一律以 `Long`（毫秒）存储，**不做** Date/LocalDate 转换 ——
 * 转换成本地日期会丢失时区信息，而统计区间换算已在 `core:common` 的 TimeRange 中统一处理，
 * 这里再转一次只会引入「两套时区口径」的隐患。
 *
 * 所有反解析都走 [parseEnum]：枚举值未知时**回退到默认值而非抛异常**，
 * 否则将来新增/重命名枚举常量时，老数据库里的旧值会让整张表读不出来（等于用户账目全丢）。
 */
class Converters {

    @TypeConverter
    fun fromBillType(value: BillType?): String? = value?.name

    @TypeConverter
    fun toBillType(value: String?): BillType? =
        value?.let { parseEnum(it, BillType.EXPENSE) }

    @TypeConverter
    fun fromSourceType(value: SourceType?): String? = value?.name

    @TypeConverter
    fun toSourceType(value: String?): SourceType? =
        value?.let { parseEnum(it, SourceType.MANUAL) }

    @TypeConverter
    fun fromAccountType(value: AccountType?): String? = value?.name

    @TypeConverter
    fun toAccountType(value: String?): AccountType? =
        value?.let { parseEnum(it, AccountType.CASH) }

    @TypeConverter
    fun fromBudgetPeriod(value: BudgetPeriod?): String? = value?.name

    @TypeConverter
    fun toBudgetPeriod(value: String?): BudgetPeriod? =
        value?.let { parseEnum(it, BudgetPeriod.MONTHLY) }

    @TypeConverter
    fun fromDailyMode(value: DailyMode?): String? = value?.name

    @TypeConverter
    fun toDailyMode(value: String?): DailyMode? =
        value?.let { parseEnum(it, DailyMode.ELASTIC) }

    @TypeConverter
    fun fromPendingReason(value: PendingReason?): String? = value?.name

    /**
     * 兜底为 [PendingReason.DIRECTION_UNKNOWN]（而不是抛异常）：这是「最不确定」的那一档，
     * 用户看到「方向待定」比整张待确认表读不出来要好得多。
     */
    @TypeConverter
    fun toPendingReason(value: String?): PendingReason? =
        value?.let { parseEnum(it, PendingReason.DIRECTION_UNKNOWN) }
}

/** 名字非法时返回 [fallback]，不抛异常 */
private inline fun <reified T : Enum<T>> parseEnum(value: String, fallback: T): T =
    runCatching { enumValueOf<T>(value) }.getOrNull() ?: fallback
