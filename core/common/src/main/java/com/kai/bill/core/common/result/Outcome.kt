package com.kai.bill.core.common.result

/**
 * 轻量的成功/失败结果封装。
 *
 * 为什么不直接用 `Result<T>`：Kotlin 标准库的 Result 携带的是 [Throwable]，
 * 而账单解析这类场景的失败原因往往是「没匹配到规则」这种**业务性失败**，
 * 不需要异常对象；同时 Result 作为 Flow 的上游类型也不够稳定。
 *
 * @param T 成功时携带的数据类型
 */
sealed interface Outcome<out T> {

    /** 操作成功，[data] 为结果数据 */
    data class Success<out T>(val data: T) : Outcome<T>

    /**
     * 操作失败。
     *
     * @property message 面向用户或日志的中文描述
     * @property cause 原始异常，可能为 null（业务性失败时没有异常）
     */
    data class Failure(val message: String, val cause: Throwable? = null) : Outcome<Nothing>
}

/**
 * 成功时执行 [action]，失败时跳过。返回自身以便链式调用。
 *
 * @param action 成功回调，参数为成功数据
 * @return 原 [Outcome]，便于继续链式处理
 */
inline fun <T> Outcome<T>.onSuccess(action: (T) -> Unit): Outcome<T> {
    if (this is Outcome.Success) action(data)
    return this
}

/**
 * 失败时执行 [action]，成功时跳过。返回自身以便链式调用。
 *
 * @param action 失败回调，参数为失败详情
 * @return 原 [Outcome]，便于继续链式处理
 */
inline fun <T> Outcome<T>.onFailure(action: (Outcome.Failure) -> Unit): Outcome<T> {
    if (this is Outcome.Failure) action(this)
    return this
}

/**
 * 把可能抛异常的块转成 [Outcome]，避免调用方到处写 try-catch。
 *
 * @param block 可能抛出异常的执行块
 * @return 成功返回 [Outcome.Success]，抛异常返回 [Outcome.Failure]
 */
inline fun <T> runCatchingOutcome(block: () -> T): Outcome<T> = try {
    Outcome.Success(block())
} catch (t: Throwable) {
    Outcome.Failure(t.message ?: "未知错误", t)
}
