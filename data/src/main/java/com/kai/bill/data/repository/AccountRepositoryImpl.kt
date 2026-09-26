package com.kai.bill.data.repository

import com.kai.bill.core.db.dao.AccountDao
import com.kai.bill.data.mapper.AccountMapper
import com.kai.bill.domain.model.Account
import com.kai.bill.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val accountDao: AccountDao
) : AccountRepository {

    override fun observeAll(): Flow<List<Account>> =
        accountDao.observeAll().map { it.map(AccountMapper::toDomain) }

    override fun observeActive(): Flow<List<Account>> =
        accountDao.observeActive().map { it.map(AccountMapper::toDomain) }

    override suspend fun getById(id: Long): Account? =
        accountDao.getById(id)?.let(AccountMapper::toDomain)

    override suspend fun insert(account: Account): Long =
        accountDao.insert(AccountMapper.toEntity(account))

    override suspend fun insertAll(accounts: List<Account>): List<Long> =
        accountDao.insertAll(accounts.map(AccountMapper::toEntity))

    override suspend fun update(account: Account) =
        accountDao.update(AccountMapper.toEntity(account))

    override suspend fun refreshIcon(id: Long, icon: String?) =
        accountDao.refreshIcon(id, icon)

    override suspend fun deleteById(id: Long) = accountDao.deleteById(id)
}
