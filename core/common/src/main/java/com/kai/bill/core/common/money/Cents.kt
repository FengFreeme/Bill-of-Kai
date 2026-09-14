package com.kai.bill.core.common.money

/**
 * 金额单位「分」的类型别名。
 *
 * 全工程金额一律以此类型（即 [Long]）存储与计算，**禁止**使用 Float / Double，
 * 避免 `0.1 + 0.2 != 0.3` 这类精度问题在账目汇总时被放大。
 *
 * 约定：所有金额变量命名必须以 `Cents` 结尾，例如 `amountCents`、`monthSpentCents`。
 */
typealias Cents = Long

/**
 * 把「元」为单位的字符串解析为「分」。
 *
 * 兼容解析支付宝/微信/银行通知中的常见写法：
 * - `"25"` → 2500
 * - `"25.5"` / `"25.50"` → 2550
 * - `"¥25.00"` / `"25元"` → 2500
 * - `"1,234.56"` → 123456（支持千分位逗号）
 *
 * @param source 待解析的金额文本，允许带货币符号、千分位与全角字符
 * @return 转换后的金额，单位「分」
 * @throws NumberFormatException 当文本中不含任何合法数字时抛出
 */
fun parseYuanToCents(source: String): Cents {
    // 清洗：去掉货币符号、千分位与单位字，并把全角数字/小数点转成半角
    val cleaned = source
        .replace(Regex("[¥￥$,\\s元圆块]"), "")
        .map { ch ->
            when (ch) {
                in '０'..'９' -> ch - 0xFEE0   // 全角数字 → 半角
                '．' -> '.'                    // 全角句点 → 半角小数点
                else -> ch
            }
        }
        .joinToString("")

    // 用 BigDecimal 解析再放大 100 倍，避免 Double 中途参与运算把 25.1 变成 2509
    return java.math.BigDecimal(cleaned)
        .setScale(2, java.math.RoundingMode.HALF_UP)
        .movePointRight(2)
        .longValueExact()
}

/**
 * 对金额求和。
 *
 * 单独提供此函数是为了让调用方不必各写一次 `sumOf { it }`，
 * 将来若需要加溢出保护也只需改这一处。
 *
 * @param amounts 待求和的金额列表，单位「分」
 * @return 求和结果，单位「分」；空列表返回 0
 */
fun sumCents(amounts: Iterable<Cents>): Cents = amounts.sum()
