package com.kai.bill.data.capture.accessibility

/**
 * 「这一屏是不是**账单详情页**」的门面 —— 按包名分发到各 App 专用规则。
 *
 * - 支付宝 → [AlipayBillPageHeuristics]（正向组合，无否定词）
 * - 微信 → [WechatBillPageHeuristics]（正向组合，无否定词）
 * - 其它 / 包名为 null → [GenericBillPageHeuristics]（详情字段 ∧ 非否定词）
 *
 * 抽成纯函数是为了用真实页面文本写回归测试，不必每次真机试错。
 */
object BillPageHeuristics {

    private val ALIPAY_PACKAGES = setOf(
        "com.eg.android.AlipayGphone",
        "com.eg.android.AlipayGphoneRC"
    )

    private val WECHAT_PACKAGES = setOf("com.tencent.mm")

    /**
     * @param text 已归一化为单行的页面文本（见 [PageTextExtractor]）
     * @param packageName 事件来源包名；为 null 时走通用规则（兼容旧调用）
     * @return true 表示可以按「某一笔交易的详情」来处理
     */
    fun isBillDetail(text: String, packageName: String? = null): Boolean = when {
        packageName != null && isAlipay(packageName) -> AlipayBillPageHeuristics.isBillDetail(text)
        packageName != null && isWechat(packageName) -> WechatBillPageHeuristics.isBillDetail(text)
        else -> GenericBillPageHeuristics.isBillDetail(text)
    }

    fun isAlipay(packageName: String): Boolean = packageName in ALIPAY_PACKAGES

    fun isWechat(packageName: String): Boolean = packageName in WECHAT_PACKAGES
}
