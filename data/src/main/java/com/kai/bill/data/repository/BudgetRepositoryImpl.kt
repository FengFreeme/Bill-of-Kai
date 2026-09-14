package com.kai.bill.data.repository

import com.kai.bill.core.db.dao.BudgetDao
import com.kai.bill.data.mapper.BudgetMapper
import com.kai.bill.domain.model.Budget
import com.kai.bill.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [BudgetRepository] 的 Room 实现。
 *
 * 注入 [BudgetDao]，通过 [BudgetMapper] 完成 Entity ⇄ Domain 转换；
 * 自身不持有任何业务逻辑，纯转发 + 映射。
 */
@Singleton
class BudgetRepositoryImpl @Inject constructor(
    private val budgetDao: BudgetDao
) : BudgetRepository {

    override fun observeAll(): Flow<List<Budget>> =
        budgetDao.observeAll().map { entities -> entities.map(BudgetMapper::toDomain) }

    override fun observeTotalBudget(): Flow<Budget?> =
        budgetDao.observeTotalBudget().map { entity -> entity?.let(BudgetMapper::toDomain) }

    override fun observeByCategory(categoryId: Long): Flow<Budget?> =
        budgetDao.observeByCategory(categoryId).map { entity -> entity?.let(BudgetMapper::toDomain) }

    override fun observeEnabled(): Flow<List<Budget>> =
        budgetDao.observeEnabled().map { entities -> entities.map(BudgetMapper::toDomain) }

    override suspend fun upsert(budget: Budget) = budgetDao.upsert(BudgetMapper.toEntity(budget))

    override suspend fun deleteById(id: Long) = budgetDao.deleteById(id)
}
