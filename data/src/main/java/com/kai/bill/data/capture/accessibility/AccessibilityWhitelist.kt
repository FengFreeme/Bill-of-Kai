package com.kai.bill.data.capture.accessibility

/**
 * 无障碍采集白名单 —— 「允许读取其页面文本的 App」。
 *
 * 与 `NotificationWhitelist` 的**分工不同，内容也不同**：
 * - 通知白名单是「会发出账单通知的 App」（支付宝 / 微信 / 银行 / 短信），是**采集闸门**；
 * - 本白名单是「用户付款时前台可能是哪个消费类 App」（美团 / 滴滴 / 京东……），
 *   用于给账单补上分类 —— 这些 App 通常**不发**账单通知，所以不在通知白名单里。
 *
 * 两份白名单都必须在 `onAccessibilityEvent` / `onNotificationPosted` 的**第一行**过滤：
 * 事件随后立即丢弃，不持久化、不写日志。只读白名单内的包，是这个功能能被用户接受的底线。
 */
object AccessibilityWhitelist {

    private val WATCHED = setOf(
        // —— 支付 / 收款：付款成功页本身常含商户名 ——
        "com.eg.android.AlipayGphone",
        "com.eg.android.AlipayGphoneRC",
        "com.tencent.mm",

        // —— 外卖 / 生鲜 ——
        "com.sankuai.meituan",
        "com.sankuai.meituan.takeoutnew",
        "me.ele",
        "com.dingdong.picmap",

        // —— 出行 ——
        "com.sdu.didi.psnger",

        // —— 电商 ——
        "com.taobao.taobao",
        "com.jingdong.app.mall",
        "com.xunmeng.pinduoduo",

        // —— 咖啡 / 茶饮 ——
        "com.luckin.coffee",
        "com.starbucks.cn"
    )

    /**
     * 该包是否在白名单内。
     *
     * @param packageName 事件来源包名
     * @return true 表示可以读取其页面文本
     */
    fun isWatched(packageName: String): Boolean = packageName in WATCHED
}
