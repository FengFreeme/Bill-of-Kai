package com.kai.bill.core.common.money

/**
 * 金额单位「分」的类型别名（即 [Long]）。
 *
 * 全工程金额一律用它存储与计算，禁止 Float / Double —— 避免 `0.1 + 0.2 != 0.3`
 * 这类精度问题在汇总时被放大。金额变量命名必须以 `Cents` 结尾，如 `amountCents`。
 */
typealias Cents = Long

/**
 * 把「元」为单位的字符串解析为「分」，兼容通知里的常见写法：
 * `"25"` / `"25.5"` / `"¥25.00"` / `"25元"` / `"1,234.56"` → 2500 / 2550 / 2500 / 2500 / 123456。
 *
 * @param source 允许带货币符号、千分位与全角字符
 * @return 单位「分」
 * @throws NumberFormatException 文本中不含任何合法数字时
 */
fun parseYuanToCents(source: String): Cents {
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

    // WHY: 用 BigDecimal，避免 Double 中途参与运算把 25.1 变成 2509
    return java.math.BigDecimal(cleaned)
        .setScale(2, java.math.RoundingMode.HALF_UP)
        .movePointRight(2)
        .longValueExact()
}

/**
 * 对金额求和；单独封装以便将来只在一处加溢出保护。
 *
 * @param amounts 单位「分」
 * @return 空列表返回 0
 */
fun sumCents(amounts: Iterable<Cents>): Cents = amounts.sum()
