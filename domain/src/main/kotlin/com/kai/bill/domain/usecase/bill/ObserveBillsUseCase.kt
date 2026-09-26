package com.kai.bill.domain.usecase.bill

import com.kai.bill.domain.model.Bill
import com.kai.bill.domain.model.BillFilter
import com.kai.bill.domain.model.DateRange
import com.kai.bill.domain.repository.BillRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 观察指定区间内的账单列表。
 *
 * 默认按交易时间倒序（由 Repository/Dao 保证）。
 */
class ObserveBillsUseCase @Inject constructor(
    private val billRepository: BillRepository
) {
    operator fun invoke(range: DateRange, filter: BillFilter? = null): Flow<List<Bill>> =
        billRepository.observeBills(range, filter)
}
