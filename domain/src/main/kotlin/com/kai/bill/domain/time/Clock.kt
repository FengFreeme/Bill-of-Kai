package com.kai.bill.domain.time

/**
 * 可注入的时间源（domain 自建，保证零依赖）。
 *
 * 业务代码禁止直接调用 `System.currentTimeMillis()`：
 * 「补记历史账单」「跨天预算」等场景需要在单测里钉死时间。
 * 运行时由 `app/di/AppModule` 绑定系统实现。
 */
fun interface Clock {

    /**
     * @return 当前时刻的毫秒时间戳（UTC epoch millis）
     */
    fun nowMillis(): Long
}

/**
 * 固定时钟，仅供测试使用。
 *
 * @param fixedMillis 固定返回的毫秒时间戳
 */
class FixedClock(private val fixedMillis: Long) : Clock {
    override fun nowMillis(): Long = fixedMillis
}
