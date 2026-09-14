package com.kai.bill.domain.usecase.bill

import com.kai.bill.domain.repository.BillRepository
import javax.inject.Inject

/** 按主键删除账单。M1 落地。 */
class DeleteBillUseCase @Inject constructor(
    private val billRepository: BillRepository
) {
    suspend operator fun invoke(id: Long) = billRepository.deleteById(id)
}
