package com.kai.bill.di

import android.content.Context
import androidx.room.Room
import com.kai.bill.core.db.KaiDatabase
import com.kai.bill.core.db.dao.AccountDao
import com.kai.bill.core.db.dao.BillDao
import com.kai.bill.core.db.dao.BudgetDao
import com.kai.bill.core.db.dao.CategoryDao
import com.kai.bill.core.db.dao.ParseRuleDao
import com.kai.bill.core.db.dao.PendingBillDao
import com.kai.bill.core.db.dao.StatsDao
import com.kai.bill.core.db.migration.ALL_MIGRATIONS
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 数据库与 DAO 的绑定。
 *
 * 实例统一在此创建：业务代码禁止自行 `Room.databaseBuilder`，否则会有多实例 + 迁移不一致的风险。
 *
 * 已会沉淀用户账单，**清库不可接受**：显式挂 [ALL_MIGRATIONS] 且不提供任何兜底 ——
 * 缺迁移时宁可让 Room 抛异常（升级后打不开，问题当天暴露），也不要用 `fallbackToDestructiveMigration`
 * 那样静默清空用户全部账目（往往要等用户发现「账单没了」才被察觉）。
 *
 * 迁移文件在 `core/db/.../migration/Migrations.kt`（与 Entity 同属 `core:db`，不在 `data`）。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KaiDatabase =
        Room.databaseBuilder(
            context,
            KaiDatabase::class.java,
            KaiDatabase.DATABASE_NAME
        )
            .addMigrations(*ALL_MIGRATIONS)
            .build()

    @Provides
    fun provideBillDao(db: KaiDatabase): BillDao = db.billDao()

    @Provides
    fun provideStatsDao(db: KaiDatabase): StatsDao = db.statsDao()

    @Provides
    fun provideCategoryDao(db: KaiDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideAccountDao(db: KaiDatabase): AccountDao = db.accountDao()

    @Provides
    fun provideBudgetDao(db: KaiDatabase): BudgetDao = db.budgetDao()

    @Provides
    fun provideParseRuleDao(db: KaiDatabase): ParseRuleDao = db.parseRuleDao()

    @Provides
    fun providePendingBillDao(db: KaiDatabase): PendingBillDao = db.pendingBillDao()
}
