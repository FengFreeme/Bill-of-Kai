package com.kai.bill.data.capture.accessibility

import com.kai.bill.data.parser.AmountNormalizer

/**
 * 微信「账单详情页」判定与金额结构抽取 —— 纯函数，不依赖 Android 类型。
 *
 * 依据真机截图（2026-09）两类版式共用同一套正向组合：
 * - 财付通原生：顶栏「全部账单」+ 字段区（支付时间 / 转账时间 / 支付方式）
 * - 小程序交易详情：Tab「交易详情」+ 同一套字段区
 *
 * **页型**：只用正向组合，**不使用否定词**。
 *
 * ```
 * (「交易详情」 || 「全部账单」) && (「支付时间」 || 「支付方式」 || 「转账时间」)
 * ```
 * - 第一段区分「详情 / 列表顶栏」与聊天、主界面（后两者两词都没有）；
 * - 第二段挡住小程序「服务」Tab（树里常仍有「交易详情」四字，但没有时间/方式字段）；
 * - 「转账时间」专补扫码/转账详情：它们常常不写「支付时间」。
 *
 * **金额**：不走「实付」标签（截图中不存在），
 * 而取「当前状态」之前的**第一个** `±x.xx`（退款页后面还有「已退款¥x.xx」，不能取最后一个）。
 */
object WechatBillPageHeuristics {

    /**
     * `±14.00` / `14.00元`；「元」可选。
     *
     * 必须带两位小数，避免把交易单号、卡号尾号当成金额。
     */
    private val AMOUNT = Regex("""([+-]?\d[\d,]*\.\d{2})\s*元?""")

    /**
     * 是否为微信单笔账单详情页。
     *
     * @param text 已归一化为单行的页面文本
     */
    fun isBillDetail(text: String): Boolean {
        if (text.isBlank()) return false
        val hasChrome = text.contains("交易详情") || text.contains("全部账单")
        if (!hasChrome) return false
        return text.contains("支付时间") ||
            text.contains("支付方式") ||
            text.contains("转账时间")
    }

    /**
     * 按版式抽取交易金额（分，恒为正）。
     *
     * 只在「当前状态」之前的片段里取第一个金额；没有该锚点或抽不到则返回 null。
     * 不回落到标签规则（宁可不建账）。
     */
    fun extractAmountCents(text: String): Long? {
        if (text.isBlank()) return null
        val statusAt = text.indexOf("当前状态").takeIf { it >= 0 } ?: return null
        val beforeStatus = text.substring(0, statusAt)
        val raw = AMOUNT.find(beforeStatus)?.groupValues?.get(1) ?: return null
        return AmountNormalizer.toCents(raw)?.takeIf { it > 0L }
    }
}
