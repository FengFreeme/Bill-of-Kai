package com.kai.bill.core.common.ext

private const val MAX_NORMALIZED_MERCHANT_LENGTH = 32

/**
 * 商户名归一化，用于生成去重哈希：同一商户在不同来源与写法下差异很大
 * （通知「肯德基(万象城店)」/ 短信「KFC 万象城」/ 账单「肯德基餐厅  」），
 * 归一化后应得到同一个 key，避免同一笔消费被记两次。
 *
 * 规则：全角转半角 → 去空白与装饰符号 → 转小写 → 截断。
 *
 * @return 长度不超过 [MAX_NORMALIZED_MERCHANT_LENGTH]，输入为空时返回空串
 */
fun String.normalizeMerchant(): String {
    // WHY: 全角转半角，否则「ＫＦＣ」与「KFC」会被当成两家商户
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

/** 超出 [maxLength] 的以省略号结尾（省略号不计入 maxLength） */
fun String.ellipsize(maxLength: Int): String =
    if (length <= maxLength) this else "${take(maxLength)}…"

/** 空白串转 null，便于可选文本统一按可空类型处理 */
fun String?.nullIfBlank(): String? = this?.takeIf { it.isNotBlank() }
