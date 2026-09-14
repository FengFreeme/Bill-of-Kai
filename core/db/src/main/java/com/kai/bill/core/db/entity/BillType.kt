package com.kai.bill.core.db.entity

/**
 * 账单类型（数据库存储形态）。
 *
 * 注意：类型**不决定**是否计入统计，是否计入由 [BillEntity.countInStats] 独立控制。
 * 例如「银行卡还信用卡」是 [TRANSFER] 且 `countInStats = false`；
 * 而「借给朋友 500 想算支出」用 [EXPENSE] 且 `countInStats = true`。
 *
 * 本枚举与 `domain` 层的同名枚举是一对一的镜像：`core:db` 按依赖矩阵不允许依赖 `:domain`，
 * 因此不能共用，转换职责在 `data/mapper`。
 */
enum class BillType {

    /** 支出，默认计入统计与预算 */
    EXPENSE,

    /** 收入，默认计入统计 */
    INCOME,

    /** 转账 / 还款 / 借贷，默认不计入统计 */
    TRANSFER
}
