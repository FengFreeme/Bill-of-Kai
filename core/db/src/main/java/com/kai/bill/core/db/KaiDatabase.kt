package com.kai.bill.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kai.bill.core.db.converter.Converters
import com.kai.bill.core.db.dao.AccountDao
import com.kai.bill.core.db.dao.BillDao
import com.kai.bill.core.db.dao.BudgetDao
import com.kai.bill.core.db.dao.CategoryDao
import com.kai.bill.core.db.dao.ParseRuleDao
import com.kai.bill.core.db.dao.StatsDao
import com.kai.bill.core.db.entity.AccountEntity
import com.kai.bill.core.db.entity.BillEntity
import com.kai.bill.core.db.entity.BudgetEntity
import com.kai.bill.core.db.entity.CategoryEntity
import com.kai.bill.core.db.entity.ParseRuleEntity

/**
 * 当前数据库版本。
 *
 * 定义为**文件级私有常量**而非 companion object 里的成员：注解参数只能是编译期常量，
 * 且这里恰好就是 [KaiDatabase] 自身的注解，若放 companion 会形成自引用。
 * 独立成常量是为了让「版本号」只有这一处真相 —— 写死两份迟早会漏改一处，
 * 而漏改的后果是 Room 走破坏性重建、用户账目全丢。
 *
 * 升级流程：本值 +1 → 在 `migration/Migrations.kt` 追加一条 Migration →
 * 加进 ALL_MIGRATIONS。
 */
private const val DATABASE_VERSION = 1

/**
 * 小凯记账的 Room 数据库 —— **唯一数据源（Single Source of Truth）**。
 *
 * 边界：本类只负责建表与提供 DAO，**不做** Entity ⇔ 领域模型的转换，
 * 转换职责在 `data/mapper`，保证 UI 层永远拿不到 Entity。
 *
 * 实例由 `app/di/DatabaseModule.kt` 以单例提供，业务代码不要自行 `Room.databaseBuilder`。
 */
@Database(
    entities = [
        BillEntity::class,
        CategoryEntity::class,
        AccountEntity::class,
        BudgetEntity::class,
        ParseRuleEntity::class
    ],
    version = DATABASE_VERSION,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class KaiDatabase : RoomDatabase() {

    abstract fun billDao(): BillDao

    abstract fun statsDao(): StatsDao

    abstract fun categoryDao(): CategoryDao

    abstract fun accountDao(): AccountDao

    abstract fun budgetDao(): BudgetDao

    abstract fun parseRuleDao(): ParseRuleDao

    companion object {

        /** 数据库文件名 */
        const val DATABASE_NAME = "kai_bill.db"
    }
}
