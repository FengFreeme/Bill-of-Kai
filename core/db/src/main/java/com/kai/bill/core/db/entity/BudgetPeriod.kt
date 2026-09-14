package com.kai.bill.core.db.entity

/**
 * 预算周期（数据库存储形态）。
 *
 * 当前只支持月预算。保留枚举而非写死字符串，是为了将来加「周预算 / 年预算」
 * 时无需改表，只需扩充枚举并在 `domain/calculator` 补分支。
 */
enum class BudgetPeriod {

    /** 自然月；配合 [com.kai.bill.core.db.entity.BudgetEntity.startDay] 可做「发薪日起算」 */
    MONTHLY
}
