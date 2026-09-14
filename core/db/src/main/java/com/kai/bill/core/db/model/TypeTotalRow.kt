package com.kai.bill.core.db.model

import com.kai.bill.core.db.entity.BillType

/**
 * 按账单类型聚合的小计行（概览：支出 / 收入 / 结余）。
 *
 * @property billType 账单类型
 * @property totalCents 该类型金额小计，单位「分」，非负
 */
data class TypeTotalRow(
    val billType: BillType,
    val totalCents: Long
)
