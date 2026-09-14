package com.kai.bill.domain.model

/**
 * 账单类型。
 *
 * 注意：类型**不决定**是否计入统计，是否计入由 [Bill.countInStats] 单独控制。
 * 这是本项目最重要的一条建模决策 —— 若把「是否计入」绑死在类型上，
 * 「银行卡还信用卡」「借给朋友的钱想算支出」「可报销先不计入」等场景就会互相打架，
 * 只能反复改表。
 *
 * 本枚举与 `core:db` 的 `BillEntity` 侧同名枚举是一一对应的镜像：
 * `domain` 按铁律 1 不能依赖任何 Android 模块，因此无法共用，转换职责在 `data/mapper`。
 */
enum class BillType {

    /** 支出，默认计入统计与预算 */
    EXPENSE,

    /** 收入，默认计入统计 */
    INCOME,

    /** 转账 / 还款 / 借贷，默认不计入统计 */
    TRANSFER
}
