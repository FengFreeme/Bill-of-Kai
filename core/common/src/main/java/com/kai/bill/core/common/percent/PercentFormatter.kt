package com.kai.bill.core.common.percent

import java.util.Locale

/**
 * 百分比格式化。
 *
 * 统一保留**两位小数**：分类/账户占比经常出现 33.33% 这类等分结果，
 * 取整后多个分类会显示成同一个数字，既看不出差异、也容易让人以为算错了。
 *
 * 固定用 [Locale.US]：某些语言环境会把小数点格式化成逗号，
 * 与金额文案（同样固定 Locale）风格不一致。
 */
object PercentFormatter {

    /**
     * @param ratio 超出 [0,1] 会被收敛到边界（脏数据不应显示成 -5% 或 300%）
     */
    fun of(ratio: Float): String =
        "%.2f%%".format(Locale.US, ratio.coerceIn(0f, 1f) * 100f)
}
