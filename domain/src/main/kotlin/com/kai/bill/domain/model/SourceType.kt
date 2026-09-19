package com.kai.bill.domain.model

/**
 * 账单来源。
 *
 * 区分来源的实际用途：
 * 1. 定位故障 —— 解析失败时可以区分是「通知文案改版」「短信模板变化」，
 *    还是「页面/截图识别不准」；
 * 2. 调整去重策略 —— [MANUAL] 的可信度高，时间窗可以放宽；
 *    [NOTIFICATION] / [SMS] / [ACCESSIBILITY] / [SCREENSHOT] 可能重复到达同一笔消费，
 *    需要严格判重；
 * 3. 决定能否被自动改写 —— 只有**自动来源**的账单才进入分类回填候选，
 *    [MANUAL] 永远排除在外（用户手输的分类不该被程序覆盖）。
 */
enum class SourceType {

    /** 通知监听捕获（支付宝 / 微信 / 银行 App 的支付通知） */
    NOTIFICATION,

    /** 短信解析（银行的交易短信） */
    SMS,

    /** 用户手动记账 */
    MANUAL,

    /**
     * L2 无障碍：由页面文本识别形成的账单。
     *
     * 出现在「通知没来、但页面上有这笔交易」的场景（见 `SignalReconciler` 的建账分支）。
     */
    ACCESSIBILITY,

    /**
     * L3 截图 OCR：由截图文本识别形成的账单。
     *
     * 出现在「通知没来、也无障碍受限（如微信账单详情页）」的场景。
     */
    SCREENSHOT
}
