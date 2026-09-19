package com.kai.bill.domain.calculator

/**
 * 金额加减算式求值 —— 键盘上的「+ / −」拼出来的式子，如 `"12+8"`、`"100-30.5"`。
 *
 * 只支持加减、从左到右算：记账场景用不到乘除与括号，也就没有优先级歧义。
 * 金额真源仍是「分」——每一项交给 [MoneyText.textToCents] 解析后按整数求和，不引入浮点误差。
 *
 * 边界一律「宽容」处理，免得用户输入到一半就被算成奇怪的值：
 * - 空串、只有运算符、结尾多一个运算符 → 对应项按 0 计；
 * - 非法片段（如 `"."`）→ 由 [MoneyText.textToCents] 归 0。
 *
 * 结果允许为负（如 `"12-20"`）：是否拦截交给调用方判断
 * （记一笔要求金额为正，见 `RecordViewModel.canSave`）。
 */
object AmountExpression {

    private const val PLUS = '+'
    private const val MINUS = '-'

    /** 键盘上显示用的「−」(U+2212)。文本里统一存 ASCII `-`，这里只是兜底认一下。 */
    private const val MINUS_SIGN = '\u2212'

    /** 文本里是否含运算符：用来判断该显示「算式」还是纯金额 */
    fun hasOperator(text: String): Boolean =
        text.any { it == PLUS || it == MINUS || it == MINUS_SIGN }

    /**
     * 把加减算式求值为「分」。
     *
     * @param text 算式文本，如 `"12"`、`"12+8"`、`"100-30.5"`
     * @return 结果，单位「分」；空串返回 0
     */
    fun toCents(text: String): Long {
        if (text.isEmpty()) return 0L
        var total = 0L
        var sign = 1L
        val term = StringBuilder()
        text.forEach { ch ->
            if (ch == PLUS || ch == MINUS || ch == MINUS_SIGN) {
                total += sign * MoneyText.textToCents(term.toString())
                term.clear()
                sign = if (ch == PLUS) 1L else -1L
            } else {
                term.append(ch)
            }
        }
        return total + sign * MoneyText.textToCents(term.toString())
    }
}
