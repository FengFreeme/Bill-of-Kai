package com.kai.bill.domain.model

/**
 * 账单 —— 本项目的核心领域模型。
 *
 * 建模要点：
 * - 金额恒为**正数**，方向由 [type] 表达；这样求和时不必区分加减，少一类符号错误
 * - [countInStats] 独立于 [type]，覆盖转账不计入、借钱算支出、可报销先不计入等全部场景
 * - [tradeTimeMillis] 是**交易发生时间**，与 [createdAt]（记录时间）严格区分；
 *   用户在 3 号补记 1 号的消费时，统计必须按 1 号归月
 *
 * @property id 主键，0 表示尚未落库（新增）
 * @property amountCents 金额，单位「分」，恒为正数
 * @property type 账单类型
 * @property countInStats 是否计入统计与预算；[BillType.TRANSFER] 默认 false
 * @property categoryId 分类 ID；[BillType.TRANSFER] 时复用为「还款 / 互转 / 借款」子类型
 * @property accountId 账户 ID，可空（未指定账户）
 * @property merchant 商户名，解析得出
 * @property note 用户备注
 * @property tradeTimeMillis 交易发生时间（毫秒）
 * @property source 账单来源
 * @property rawText 原始通知/短信全文，解析失败排查与规则更新后重解析的依据
 * @property dedupHash 去重键：md5(金额 + 分钟级时间 + 归一化商户)
 * @property createdAt 记录创建时间（毫秒）
 * @property updatedAt 记录最后修改时间（毫秒）
 */
data class Bill(

    val id: Long = 0,

    val amountCents: Long,

    val type: BillType,

    val countInStats: Boolean = true,

    val categoryId: Long,

    val accountId: Long?,

    val merchant: String?,

    val note: String?,

    val tradeTimeMillis: Long,

    val source: SourceType,

    val rawText: String?,

    val dedupHash: String,

    val createdAt: Long,

    val updatedAt: Long
) {

    /**
     * 该账单是否参与支出 / 收入统计与预算计算。
     *
     * 单独提供而非让调用方各处写 `type != TRANSFER`：统计口径只允许有一处定义，
     * 否则将来调整规则时必然漏改某一处查询。
     */
    val isCounted: Boolean
        get() = countInStats && (type == BillType.EXPENSE || type == BillType.INCOME)
}
