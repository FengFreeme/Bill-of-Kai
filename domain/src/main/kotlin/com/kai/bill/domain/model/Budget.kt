package com.kai.bill.domain.model

/**
 * 预算。
 *
 * 本文件同时承载 [BudgetPeriod] 与 [DailyMode] 两个枚举：它们只服务于预算，
 * 拆成独立文件反而让「预算相关的东西」散落三处。
 *
 * @property id 主键，0 表示尚未落库
 * @property categoryId 分类 ID；**为 null 表示总额预算**。预留分类预算，将来支持时无需改表
 * @property period 预算周期
 * @property amountCents 月预算金额，单位「分」；0 表示用户未设置预算
 * @property startDay 周期起始日，取值 1~31，用于「发薪日起算」；当月不足该天数时退化为月末
 * @property dailyMode 日预算计算模式
 * @property dailyAmountCents 固定日预算金额，单位「分」；仅 [DailyMode.FIXED] 生效
 * @property carryOver 是否把本月结余结转到下月
 * @property enabled 是否启用；关闭后首页不渲染预算卡，但保留历史设置便于重新打开
 */
data class Budget(

    val id: Long = 0,

    val categoryId: Long?,

    val period: BudgetPeriod,

    val amountCents: Long,

    val startDay: Int,

    val dailyMode: DailyMode,

    val dailyAmountCents: Long,

    val carryOver: Boolean,

    val enabled: Boolean
) {

    /** 是否为总额预算（相对的是分类预算） */
    val isTotalBudget: Boolean
        get() = categoryId == null
}

/**
 * 预算周期。
 *
 * 当前只支持月预算。保留枚举而非写死字符串，是为了将来加「周预算 / 年预算」
 * 时无需改表，只需扩充枚举并在 [com.kai.bill.domain.calculator.BudgetCalculator] 补分支。
 */
enum class BudgetPeriod {

    /** 自然月；配合 [Budget.startDay] 可做「发薪日起算」 */
    MONTHLY
}

/**
 * 日预算计算模式。
 *
 * 见框架文档 §8.3：
 * - [ELASTIC]：今日可用 = 本月剩余 ÷ 本月剩余天数，昨天省下的钱自动摊到今天
 * - [FIXED]：今日可用 = 固定日预算，与月预算互不影响
 */
enum class DailyMode {

    /** 弹性日预算（默认）：今天花超了明天就少花，符合「心理账户」直觉 */
    ELASTIC,

    /** 固定日预算：每天额度恒定，适合强控制欲的记账方式 */
    FIXED
}
