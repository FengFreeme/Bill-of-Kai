package com.kai.bill.data.mapper

import com.kai.bill.core.db.entity.BudgetEntity
import com.kai.bill.core.db.entity.BudgetPeriod as EntityBudgetPeriod
import com.kai.bill.core.db.entity.DailyMode as EntityDailyMode
import com.kai.bill.domain.model.Budget
import com.kai.bill.domain.model.BudgetPeriod
import com.kai.bill.domain.model.DailyMode

/** Budget Entity ⇄ Domain */
object BudgetMapper {

    fun toDomain(entity: BudgetEntity): Budget = Budget(
        id = entity.id,
        categoryId = entity.categoryId,
        period = entity.period.toDomain(),
        amountCents = entity.amountCents,
        startDay = entity.startDay,
        dailyMode = entity.dailyMode.toDomain(),
        dailyAmountCents = entity.dailyAmountCents,
        carryOver = entity.carryOver,
        enabled = entity.enabled
    )

    fun toEntity(budget: Budget): BudgetEntity = BudgetEntity(
        id = budget.id,
        categoryId = budget.categoryId,
        period = budget.period.toEntity(),
        amountCents = budget.amountCents,
        startDay = budget.startDay,
        dailyMode = budget.dailyMode.toEntity(),
        dailyAmountCents = budget.dailyAmountCents,
        carryOver = budget.carryOver,
        enabled = budget.enabled
    )
}

private fun EntityBudgetPeriod.toDomain(): BudgetPeriod = when (this) {
    EntityBudgetPeriod.MONTHLY -> BudgetPeriod.MONTHLY
}

private fun BudgetPeriod.toEntity(): EntityBudgetPeriod = when (this) {
    BudgetPeriod.MONTHLY -> EntityBudgetPeriod.MONTHLY
}

private fun EntityDailyMode.toDomain(): DailyMode = when (this) {
    EntityDailyMode.ELASTIC -> DailyMode.ELASTIC
    EntityDailyMode.FIXED -> DailyMode.FIXED
}

private fun DailyMode.toEntity(): EntityDailyMode = when (this) {
    DailyMode.ELASTIC -> EntityDailyMode.ELASTIC
    DailyMode.FIXED -> EntityDailyMode.FIXED
}
