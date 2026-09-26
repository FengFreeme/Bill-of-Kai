package com.kai.bill.core.common.result

/**
 * 轻量的成功/失败结果封装。
 *
 * 不用标准库 `Result<T>`：它的失败必带 [Throwable]，而账单解析的失败多是「没匹配到规则」
 * 这类**业务性失败**，无需异常对象；且 Result 作为 Flow 上游类型也不够稳定。
 */
sealed interface Outcome<out T> {

    data class Success<out T>(val data: T) : Outcome<T>

    /**
     * @property message 面向用户或日志的中文描述
     * @property cause 业务性失败时为 null
     */
    data class Failure(val message: String, val cause: Throwable? = null) : Outcome<Nothing>
}

/** 成功时执行 [action]，失败时跳过；返回自身以便链式调用 */
inline fun <T> Outcome<T>.onSuccess(action: (T) -> Unit): Outcome<T> {
    if (this is Outcome.Success) action(data)
    return this
}

/** 失败时执行 [action]，成功时跳过；返回自身以便链式调用 */
inline fun <T> Outcome<T>.onFailure(action: (Outcome.Failure) -> Unit): Outcome<T> {
    if (this is Outcome.Failure) action(this)
    return this
}

/** 把可能抛异常的块转成 [Outcome]，避免调用方到处写 try-catch */
inline fun <T> runCatchingOutcome(block: () -> T): Outcome<T> = try {
    Outcome.Success(block())
} catch (t: Throwable) {
    Outcome.Failure(t.message ?: "未知错误", t)
}
