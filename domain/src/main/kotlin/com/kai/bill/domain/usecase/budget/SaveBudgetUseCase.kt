package com.kai.bill.domain.usecase.budget

import com.kai.bill.domain.model.Budget
import com.kai.bill.domain.repository.BudgetRepository
import javax.inject.Inject

/** 保存预算配置。M3 落地。 */
class SaveBudgetUseCase @Inject constructor(
    private val budgetRepository: BudgetRepository
) {
    suspend operator fun invoke(budget: Budget) = budgetRepository.upsert(budget)
}
