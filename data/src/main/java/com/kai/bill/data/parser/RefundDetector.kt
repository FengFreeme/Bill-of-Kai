package com.kai.bill.data.parser

import com.kai.bill.domain.model.RefundCategory

/**
 * 退款识别 —— 纯函数，可单测。
 *
 * NOTE: 必须在解析阶段认定并存进 `Bill.isRefund`：退款落库时分类会被改写成原消费的分类，
 * 之后再想从「分类 = 退款」反推就认不出来了。
 *
 * 调用前提：**只对收入方向调用**。支出页面出现「7 天无理由退款」很常见，
 * 把一笔正常消费判成退款会去冲抵另一笔消费，属于记错账。
 */
object RefundDetector {

    /** 退款字样；不含「报销 / 理赔」—— 那是真实进账，不是支出的逆操作 */
    private val REFUND_WORDS = listOf("退款", "退还", "退回", "退货")

    /**
     * @param directionKeyword 方向路由命中的关键词；判不出方向时为 null
     * @param categoryId 分类路由结果；命中「退款」分类时直接判定为退款
     */
    fun isRefund(rawText: String, directionKeyword: String?, categoryId: Long): Boolean {
        if (categoryId == RefundCategory.ID) return true
        if (directionKeyword != null && REFUND_WORDS.any { directionKeyword.contains(it) }) return true
        return REFUND_WORDS.any { rawText.contains(it) }
    }
}
