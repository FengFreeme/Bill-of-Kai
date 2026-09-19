package com.kai.bill.di

import com.kai.bill.data.repository.AccountRepositoryImpl
import com.kai.bill.data.repository.BackupRepositoryImpl
import com.kai.bill.data.repository.BillRepositoryImpl
import com.kai.bill.data.repository.BudgetRepositoryImpl
import com.kai.bill.data.repository.CategoryRepositoryImpl
import com.kai.bill.data.repository.ParseRuleRepositoryImpl
import com.kai.bill.data.repository.PendingBillRepositoryImpl
import com.kai.bill.data.repository.StatsRepositoryImpl
import com.kai.bill.domain.repository.AccountRepository
import com.kai.bill.domain.repository.BackupRepository
import com.kai.bill.domain.repository.BillRepository
import com.kai.bill.domain.repository.BudgetRepository
import com.kai.bill.domain.repository.CategoryRepository
import com.kai.bill.domain.repository.ParseRuleRepository
import com.kai.bill.domain.repository.PendingBillRepository
import com.kai.bill.domain.repository.StatsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 仓库接口绑定（M1 落地，M2 增补统计仓储，M4 增补解析规则仓储）。
 *
 * 接口注入 `domain`，实现在 `data`；统一在此以 `@Binds` 收口。
 * 业务层只依赖接口，将来换 `CloudBillRepository` 只需改这一处绑定。
 */
@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {

    @Binds
    @Singleton
    fun bindBillRepository(impl: BillRepositoryImpl): BillRepository

    @Binds
    @Singleton
    fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    @Singleton
    fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    @Singleton
    fun bindStatsRepository(impl: StatsRepositoryImpl): StatsRepository

    @Binds
    @Singleton
    fun bindBudgetRepository(impl: BudgetRepositoryImpl): BudgetRepository

    @Binds
    @Singleton
    fun bindParseRuleRepository(impl: ParseRuleRepositoryImpl): ParseRuleRepository

    @Binds
    @Singleton
    fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository

    @Binds
    @Singleton
    fun bindPendingBillRepository(impl: PendingBillRepositoryImpl): PendingBillRepository
}
