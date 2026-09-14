package com.kai.bill.core.common.ext

/** 归一化后商户名的最大长度，超出部分截断 */
private const val MAX_NORMALIZED_MERCHANT_LENGTH = 32

/**
 * 商户名归一化，用于生成去重哈希。
 *
 * 同一商户在不同来源里的写法差异很大，例如：
 * - 通知： `肯德基(万象城店)`
 * - 短信： `KFC 万象城`
 * - 账单： `肯德基餐厅  `
 *
 * 归一化规则：全角转半角 → 去掉空白与装饰符号 → 转小写 → 截断。
 * 目标是让「同一商户」尽可能得到同一个 key，避免同一笔消费被记两次。
 *
 * @return 归一化后的字符串，长度不超过 32；输入为空时返回空串
 */
fun String.normalizeMerchant(): String {
    // 全角字符（！～ 区间，含全角字母数字）统一转成半角，否则 "ＫＦＣ" 和 "KFC" 会被当成两家商户
    val halfWidth = this.map { ch ->
        when (ch) {
            in '！'..'～' -> ch - 0xFEE0
            '　' -> ' '
            else -> ch
        }
    }.joinToString("")

    return halfWidth
        .replace(Regex("[\\s\\-_*·.。()（）【】\\[\\]『』「」\"']"), "")
        .lowercase()
        .take(MAX_NORMALIZED_MERCHANT_LENGTH)
}

/**
 * 截取字符串，超出部分以省略号结尾。
 *
 * @param maxLength 最大字符数（不含省略号）
 * @return 原串或截断后的串
 */
fun String.ellipsize(maxLength: Int): String =
    if (length <= maxLength) this else "${take(maxLength)}…"

/**
 * 空串转 null，便于把可选文本统一成可空类型处理。
 *
 * @return 空白串返回 null，否则返回原串
 */
fun String?.nullIfBlank(): String? = this?.takeIf { it.isNotBlank() }
