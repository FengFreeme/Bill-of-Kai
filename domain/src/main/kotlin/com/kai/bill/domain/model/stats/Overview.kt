package com.kai.bill.domain.model.stats

/**
 * 区间概览：支出 / 收入 / 结余。
 *
 * 口径提醒：这里的支出与收入**都只包含计入统计的账单**（[com.kai.bill.domain.model.Bill.countInStats] 为 true），
 * 转账还款不会混入，否则「本月支出」会被还信用卡的金额撑得虚高。
 *
 * @property expenseCents 支出合计，单位「分」，非负
 * @property incomeCents 收入合计，单位「分」，非负
 */
data class Overview(
    val expenseCents: Long,
    val incomeCents: Long
) {

    /**
     * 结余 = 收入 - 支出，单位「分」；为负表示入不敷出。
     *
     * 做成计算属性而非构造参数：结余恒等于两者之差，若允许外部传入
     * 就可能出现三个字段互相矛盾的状态。
     */
    val balanceCents: Long
        get() = incomeCents - expenseCents

    /** 本区间内是否完全没有流水（用于首页判断是否展示空态） */
    val isEmpty: Boolean
        get() = expenseCents == 0L && incomeCents == 0L

    companion object {

        /** 空概览：无数据时的默认值 */
        val EMPTY = Overview(expenseCents = 0L, incomeCents = 0L)
    }
}
