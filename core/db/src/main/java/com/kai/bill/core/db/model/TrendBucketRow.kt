package com.kai.bill.core.db.model

/**
 * 趋势图的一个数据点。
 *
 * @property bucket 时间桶标签，格式为 `yyyy-MM-dd`（按设备本地时区切分）
 * @property totalCents 该时间桶的金额小计，单位「分」，非负
 */
data class TrendBucketRow(
    val bucket: String,
    val totalCents: Long
)
