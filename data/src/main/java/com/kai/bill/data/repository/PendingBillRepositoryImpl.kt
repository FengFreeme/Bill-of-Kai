package com.kai.bill.data.repository

import com.kai.bill.core.db.dao.PendingBillDao
import com.kai.bill.data.mapper.PendingBillMapper
import com.kai.bill.data.mapper.toEntity
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.PendingBill
import com.kai.bill.domain.repository.PendingBillRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [PendingBillRepository] 的 Room 实现。
 *
 * 纯转发 + 映射，没有任何业务判断：判不判得准由解析链路负责，
 * 采不采纳建议由用户负责，本层只保证「存得进、读得出、删得掉」。
 */
@Singleton
class PendingBillRepositoryImpl @Inject constructor(
    private val dao: PendingBillDao
) : PendingBillRepository {

    override fun observeAll(): Flow<List<PendingBill>> =
        dao.observeAll().map { rows -> rows.map(PendingBillMapper::toDomain) }

    override fun observeCount(): Flow<Int> = dao.observeCount()

    override suspend fun enqueue(bill: PendingBill): Long = dao.insert(PendingBillMapper.toEntity(bill))

    override suspend fun existsInWindow(
        amountCents: Long,
        suggestedType: BillType?,
        timeMillis: Long,
        windowMillis: Long
    ): Boolean = dao.existsInWindow(
        amountCents = amountCents,
        suggestedType = suggestedType?.toEntity(),
        startMillis = timeMillis - windowMillis,
        endMillis = timeMillis + windowMillis
    )

    override suspend fun getAll(): List<PendingBill> = dao.getAll().map(PendingBillMapper::toDomain)

    override suspend fun getById(id: Long): PendingBill? =
        dao.getById(id)?.let(PendingBillMapper::toDomain)

    override suspend fun deleteById(id: Long) = dao.deleteById(id)

    override suspend fun deleteByIds(ids: List<Long>) {
        // `DELETE ... WHERE id IN ()` 在 SQLite 里是语法错误，空列表直接短路
        if (ids.isEmpty()) return
        dao.deleteByIds(ids)
    }

    override suspend fun deleteOlderThan(cutoffMillis: Long): Int = dao.deleteOlderThan(cutoffMillis)
}
