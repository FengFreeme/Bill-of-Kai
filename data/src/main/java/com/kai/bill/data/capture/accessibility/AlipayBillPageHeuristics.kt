package com.kai.bill.data.capture.accessibility

import com.kai.bill.data.parser.AmountNormalizer

/**
 * 支付宝「账单详情页」判定与金额结构抽取 —— 纯函数，不依赖 Android 类型。
 *
 * 依据真机截图（2026-09）统一版式：
 * ```
 * 全部账单 → 账单详情 → 商户名 → ±金额 → 交易成功/退款成功 → 字段区 → 账单管理
 * ```
 *
 * **页型**：只用正向组合，**不使用否定词**。
 *
 * ```
 * 「账单详情」 ∧ （「全部账单」 ∨ 「账单管理」）
 * ```
 * - 「账单详情」必须有（页面标题，优先锚点）；
 * - 「全部账单」「账单管理」二选一即可（顶栏返回 / 底栏入口）。
 *
 * **金额**：不走「实付 / 付款金额」标签（截图中不存在），
 * 而取「账单详情」之后、紧挨「交易成功 / 退款成功」上方的 `±x.xx`。
 */
object AlipayBillPageHeuristics {

    /**
     * 金额正下方的状态文案。
     *
     * 六张真机截图里多数是「交易成功」，第四张退款页是「退款成功」——
     * 二者都必须认，不能只锚一个词。
     */
    private val STATUS_AFTER_AMOUNT = listOf("交易成功", "退款成功")

    /**
     * `±55.00` / `55.84元` 紧挨状态行。
     * 「元」可选：dump 文本有时带「元」，截图主金额通常不带。
     */
    private val AMOUNT_BEFORE_STATUS = Regex(
        """([+-]?\d[\d,]*\.\d{2})\s*元?\s*(?:交易成功|退款成功)"""
    )

    /**
     * 是否为支付宝单笔账单详情页。
     *
     * 必须含「账单详情」；再配「全部账单」或「账单管理」之一。
     *
     * @param text 已归一化为单行的页面文本
     */
    fun isBillDetail(text: String): Boolean {
        if (text.isBlank()) return false
        if (!text.contains("账单详情")) return false
        return text.contains("全部账单") || text.contains("账单管理")
    }

    /**
     * 按版式抽取交易金额（分，恒为正）。
     *
     * 优先在「账单详情」之后的片段里匹配；匹配不到再扫全文。
     * 抽不到返回 null（宁可不建账）。
     */
    fun extractAmountCents(text: String): Long? {
        if (text.isBlank()) return null
        val fromDetail = text.indexOf("账单详情").takeIf { it >= 0 }?.let { text.substring(it) }
        val scoped = fromDetail ?: text
        val raw = AMOUNT_BEFORE_STATUS.find(scoped)?.groupValues?.get(1) ?: return null
        return AmountNormalizer.toCents(raw)?.takeIf { it > 0L }
    }

    /** 供测试 / 诊断：当前认可的状态词 */
    internal fun statusMarkers(): List<String> = STATUS_AFTER_AMOUNT
}
