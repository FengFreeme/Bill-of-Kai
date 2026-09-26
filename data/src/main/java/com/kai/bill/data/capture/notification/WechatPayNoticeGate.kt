package com.kai.bill.data.capture.notification

/**
 * 微信通知的放行判定（采集闸门的第二道，第一道是 [NotificationWhitelist] 的包名）。
 *
 * 微信的通知绝大多数与账无关（聊天、视频号、公众号、系统提示），
 * 这里只放行带「微信支付」字样的通知，其余一律不通过。
 * 其它包不走本闸门 —— 它们的去留由流水线的四级路由决定。
 */
object WechatPayNoticeGate {

    private const val WECHAT = "com.tencent.mm"

    private const val PAY_KEYWORD = "微信支付"

    /**
     * @param packageName 通知来源包名
     * @param rawText 抽取并归一化后的通知文本（标题 + 正文，见 [NotificationTextExtractor]）
     * @return true 表示这条通知可以进入解析
     */
    fun passes(packageName: String, rawText: String): Boolean =
        packageName != WECHAT || rawText.contains(PAY_KEYWORD)
}
