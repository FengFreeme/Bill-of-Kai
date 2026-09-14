package com.kai.bill.core.common.ext

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 在指定时间窗口内只放行第一个事件，窗口内后续事件全部丢弃。
 *
 * 与 `debounce` 的区别：debounce 会**延迟**到最后一次事件后再发射，
 * 而这里要的是「立刻响应首次、然后冷却」——
 * 用于防止用户连点按钮、或短时间内收到多条重复短信时重复落库。
 *
 * @param windowMillis 冷却窗口长度（毫秒），期间的新事件会被丢弃
 * @return 经过节流处理的新 Flow
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
