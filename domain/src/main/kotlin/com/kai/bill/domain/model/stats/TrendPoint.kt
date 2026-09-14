package com.kai.bill.domain.model.stats

/**
 * 趋势图上的一个数据点。
 *
 * 只存 [startMillis] 而不存标签字符串：图表横轴既需要数值（Vico 用）也需要文案（轴标签），
 * 存毫秒可以让 UI 层按自己的格式器随时渲染，存字符串则会把「格式」硬编码进数据层，
 * 换个日期格式就得改 `data` 模块。
 *
 * @property startMillis 该时间桶的起始时刻（毫秒）。日桶为当天 00:00，月桶为当月 1 日 00:00
 * @property amountCents 该时间桶的金额合计，单位「分」，非负
 */
data class TrendPoint(
    val startMillis: Long,
    val amountCents: Long
)
