package com.kai.bill.core.common.ext

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 与 `debounce` 的区别：debounce 会延迟到最后一次事件后再发射，这里要「立刻响应首次、然后冷却」——
 * 用于防连点、或短时间内多条重复短信重复落库。
 *
 * @param windowMillis 冷却窗口长度（毫秒），期间的新事件会被丢弃
 */
fun <T> Flow<T>.throttleFirst(windowMillis: Long): Flow<T> = flow {
    var lastEmitAt = 0L
    collect { value ->
        val now = System.currentTimeMillis()
        if (now - lastEmitAt >= windowMillis) {
            lastEmitAt = now
            emit(value)
        }
    }
}
