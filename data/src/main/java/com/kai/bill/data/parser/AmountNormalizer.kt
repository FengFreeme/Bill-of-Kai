package com.kai.bill.data.parser

import java.math.BigDecimal

/**
 * ¥25.00 / 25元 / 1,234.56 / ￥100 → 分（Long）。
 *
 * 只保留数字、小数点与千位分隔符，去掉货币符号（¥ ￥ 元）、空格等；
 * 用 BigDecimal 转「分」避免浮点误差。无法解析返回 null。
 */
object AmountNormalizer {

    private val NON_NUMERIC = Regex("[^\\d.,]")

    fun toCents(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.replace(NON_NUMERIC, "").replace(",", "").trim()
        if (cleaned.isEmpty()) return null
        val value = runCatching { BigDecimal(cleaned) }.getOrNull() ?: return null
        if (value < BigDecimal.ZERO) return null
        return runCatching { value.movePointRight(2).longValueExact() }.getOrNull()
    }
}
