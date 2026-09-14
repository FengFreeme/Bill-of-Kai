package com.kai.bill.core.common.money

/**
 * 金额格式化工具：把「分」转成用于界面展示的「元」字符串。
 *
 * 全程使用整数运算（先拆出元与分，再手工插入千分位），
 * **不经过 Double**，因此不会出现 25.10 被格式化成 25.1 或 25.09 的情况。
 *
 * 注意：本类只负责「显示」，任何需要再次入账的计算都不得使用其输出。
 */
object MoneyFormatter {

    /**
     * 格式化为带千分位的元字符串，不含货币符号。
     *
     * @param cents 金额，单位「分」，允许为负
     * @return 形如 `1,234.50`、`0.05`、`-30.00` 的字符串
     */
    fun plain(cents: Cents): String {
        val negative = cents < 0
        // Long.MIN_VALUE 取绝对值会溢出，这里做一次饱和处理（实际金额远达不到该量级）
        val abs = if (cents == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(cents)
        val yuan = abs / 100
        val fen = abs % 100

        return buildString {
            if (negative) append('-')
            append(groupThousands(yuan))
            append('.')
            append(fen.toString().padStart(2, '0'))
        }
    }

    /**
     * 格式化为带人民币符号的字符串。
     *
     * @param cents 金额，单位「分」
     * @return 形如 `¥1,234.50`、`-¥30.00` 的字符串
     */
    fun withSymbol(cents: Cents): String {
        val body = plain(cents)
        // 负号放在符号之前，符合中文书写习惯（-¥30.00 而非 ¥-30.00）
        return if (body.startsWith("-")) "-¥${body.substring(1)}" else "¥$body"
    }

    /**
     * 按账单方向格式化：支出带 `-`、收入带 `+`、转账不带符号。
     *
     * @param cents 金额（分），传入**正数**即可，符号由 [direction] 决定
     * @param direction 展示方向：0 = 无符号，1 = 收入（+），-1 = 支出（-）
     * @param showSymbol 是否附加 `¥`
     * @return 形如 `-¥25.00`、`+¥3,000.00`、`¥100.00` 的字符串
     */
    fun signed(cents: Cents, direction: Int, showSymbol: Boolean = true): String {
        // NOTE: 参数名不可叫 withSymbol —— 会遮蔽本类的 withSymbol() 成员函数，
        //       导致 withSymbol(cents) 被解析成「对 Boolean 发起调用」而编译失败
        val body = if (showSymbol) withSymbol(cents) else plain(cents)
        return when {
            direction < 0 -> if (body.startsWith("-")) body else "-$body"
            direction > 0 -> "+$body"
            else -> body
        }
    }

    /**
     * 给整数部分插入千分位逗号。
     *
     * @param value 非负整数
     * @return 形如 `1234567 → 1,234,567`
     */
    private fun groupThousands(value: Long): String {
        if (value == 0L) return "0"
        val digits = value.toString()
        val result = StringBuilder()
        // 从末位往前每三位插一个逗号：剩余位数 (fromEnd - 1) 为 3 的倍数时插入
        digits.forEachIndexed { index, ch ->
            val fromEnd = digits.length - index
            result.append(ch)
            if (fromEnd > 1 && (fromEnd - 1) % 3 == 0) result.append(',')
        }
        return result.toString()
    }
}
