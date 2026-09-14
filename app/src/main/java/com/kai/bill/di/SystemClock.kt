package com.kai.bill.di

import com.kai.bill.domain.time.Clock

/**
 * 真实系统时钟 —— domain [Clock] 的默认运行时实现。
 *
 * 放在 app/di：实现依赖系统 API，不应进入纯 JVM 的 domain 模块。
 */
class SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
