package com.kai.bill.di

import android.content.Context
import androidx.room.Room
import com.kai.bill.core.db.KaiDatabase
import com.kai.bill.core.db.dao.AccountDao
import com.kai.bill.core.db.dao.BillDao
import com.kai.bill.core.db.dao.BudgetDao
import com.kai.bill.core.db.dao.CategoryDao
import com.kai.bill.core.db.dao.ParseRuleDao
import com.kai.bill.core.db.dao.StatsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 数据库与 DAO 的绑定。
 *
 * 实例统一在此创建：业务代码禁止自行 `Room.databaseBuilder`，
 * 否则会有多实例 + 迁移不一致的风险。
 *
 * 迁移策略：M1 起应用已会播种真实分类/账户并沉淀用户账单，清库不可接受。
 * 当前 `DATABASE_VERSION = 1` 尚未演进，**暂留** `fallbackToDestructiveMigration()`
 * 仅用于开发期快速迭代；**一旦 `DATABASE_VERSION` 自增，必须在此 `addMigrations(...)`
 * 补真实 `Migration`（见 `data/migration/Migrations.kt`），并移除该 fallback**，
 * 否则旧版用户升级时会丢失全部账目。
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
            .fallbackToDestructiveMigration(dropAllTables = true)
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
}
