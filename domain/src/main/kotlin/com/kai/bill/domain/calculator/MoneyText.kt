package com.kai.bill.domain.calculator

/**
 * 金额文本换算 —— 「元」字符串 ↔ 「分」。
 *
 * 纯函数、零依赖，供记一笔键盘输入与编辑回填共用。
 * 金额真源始终是「分」；UI 层只持有用户正在编辑的元字符串。
 */
object MoneyText {

    /**
     * 把用户输入的「元」字符串转为「分」。
     *
     * 规则：空串 → 0；小数最多取两位（不足补 0）；非法片段按 0 处理。
     *
     * @param text 元字符串，如 `"12"`、`"12.3"`、`"12.34"`
     * @return 金额，单位「分」
     */
    fun textToCents(text: String): Long {
        if (text.isEmpty()) return 0L
        val parts = text.split(".")
        val yuan = parts[0].toLongOrNull() ?: 0L
        val fen = if (parts.size > 1) {
            parts[1].padEnd(2, '0').take(2).toLongOrNull() ?: 0L
        } else {
            0L
        }
        return yuan * 100 + fen
    }

    /**
     * 把「分」格式化为固定两位小数的「元」字符串。
     *
     * @param cents 金额，单位「分」（取绝对值，符号由账单类型表达）
     * @return 如 `"12.34"`
     */
    fun centsToText(cents: Long): String {
        val abs = kotlin.math.abs(cents)
        val yuan = abs / 100
        val fen = abs % 100
        return buildString {
            append(yuan)
            append('.')
            append(fen.toString().padStart(2, '0'))
        }
    }
}
