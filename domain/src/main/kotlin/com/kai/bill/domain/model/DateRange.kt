package com.kai.bill.domain.model

/**
 * 时间区间 —— 贯穿统计、流水与预算的**统一抽象**（对应需求 R3）。
 *
 * 为什么单独建模而不用 `LongRange`：业务上它是「闭区间的毫秒范围」，
 * 独立成类型后可以在构造时校验 [startMillis] <= [endMillis]，
 * 避免调用方把两个参数写反却要到 SQL 层才发现查不到数据。
 *
 * @property startMillis 起始时刻（毫秒，含）
 * @property endMillis 结束时刻（毫秒，含）
 * @throws IllegalArgumentException 当起始晚于结束时抛出
 */
data class DateRange(
    val startMillis: Long,
    val endMillis: Long
) {

    init {
        require(startMillis <= endMillis) {
            "时间区间起始必须不晚于结束，收到：start=$startMillis, end=$endMillis"
        }
    }

    /**
     * 判断某个时刻是否落在本区间内。
     *
     * @param millis 待判断的时刻（毫秒）
     * @return 命中返回 true（含边界）
     */
    fun contains(millis: Long): Boolean = millis in startMillis..endMillis
}
