package com.kai.bill.domain.model

/**
 * 「退款」分类在分类表里的落点（收入侧那条，id 由 `DefaultCategories` 建库时写入）。
 *
 * 放在 domain：统计用例要用它 —— 退款是支出侧的负项，但它自己的分类在收入侧，
 * 因此占比时不参与一级上卷（否则会在支出构成里冒出「其他收入」），要单列一条负值。
 */
object RefundCategory {

    /** 「退款」分类 ID */
    const val ID = 94L
}
