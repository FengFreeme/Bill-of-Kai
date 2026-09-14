package com.kai.bill.data.repository

import com.kai.bill.core.db.dao.BillDao
import com.kai.bill.data.mapper.BillMapper
import com.kai.bill.data.mapper.toEntity
import com.kai.bill.domain.model.Bill
import com.kai.bill.domain.model.BillFilter
import com.kai.bill.domain.model.DateRange
import com.kai.bill.domain.repository.BillRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [BillRepository] 的 Room 实现。
 *
 * 注入 [BillDao]，通过 [BillMapper] 完成 Entity ⇄ Domain 转换；
 * 自身不持有任何业务逻辑，纯转发 + 映射。
 */
@Singleton
class BillRepositoryImpl @Inject constructor(
    private val billDao: BillDao
) : BillRepository {

    override fun observeBills(range: DateRange, filter: BillFilter?): Flow<List<Bill>> =
        billDao.observeFiltered(
            startMillis = range.startMillis,
            endMillis = range.endMillis,
            type = filter?.type?.toEntity(),
            // 只选中一个时交给 SQL（走索引、开销最低）；多选时传 null 跳过该维度，
            // 由下面的内存过滤兜底。个人记账的数据量在千级，这点开销远小于
            // 为「任意个数的 IN 查询」写动态 SQL 带来的复杂度与风险。
            categoryId = filter?.categoryIds?.singleOrNull(),
            accountId = filter?.accountIds?.singleOrNull(),
            countInStats = filter?.countInStats
        ).map { entities ->
            if (filter == null) {
                entities.map(BillMapper::toDomain)
            } else {
                entities.mapNotNull { entity ->
                    if (filter.matches(
                            categoryId = entity.categoryId,
                            accountId = entity.accountId
                        )
                    ) {
                        BillMapper.toDomain(entity)
                    } else {
                        null
                    }
                }
            }
        }

    /**
     * 判断单条账单是否命中筛选条件。
     *
     * 账户维度的判定要小心：只勾了「未指定账户」时，
     * [BillFilter.accountIds] 是空的，若此时不做排他处理，
     * 所有有账户的流水都会被当成「未筛选」而全部放行。
     */
    private fun BillFilter.matches(categoryId: Long?, accountId: Long?): Boolean {
        if (categoryIds.isNotEmpty() && categoryId !in categoryIds) return false

        val accountFilterActive = accountIds.isNotEmpty() || includeUnspecifiedAccount
        if (accountFilterActive) {
            if (accountId == null) {
                if (!includeUnspecifiedAccount) return false
            } else if (accountId !in accountIds) {
                return false
            }
        }
        return true
    }

    override fun observeById(id: Long): Flow<Bill?> =
        billDao.observeById(id).map { entity -> entity?.let(BillMapper::toDomain) }

    override suspend fun getById(id: Long): Bill? =
        billDao.getById(id)?.let(BillMapper::toDomain)

    override suspend fun save(bill: Bill): Long {
        val entity = BillMapper.toEntity(bill)
        return if (bill.id == 0L) {
            billDao.insert(entity)
        } else {
            billDao.update(entity)
            bill.id
        }
    }

    override suspend fun deleteById(id: Long) = billDao.deleteById(id)
}
