package com.kai.bill.core.db.entity

/**
 * 账单来源（数据库存储形态）。
 *
 * 区分来源的意义：解析失败时可按来源定位是「通知改版」「短信模板变化」还是
 * 「页面/截图识别不准」，另外 [MANUAL] 的数据可信度更高、且**不参与自动分类回填**。
 *
 * NOTE: 本枚举以 TEXT 存储，新增取值**不需要数据库迁移**；
 *       但 `Converters.toSourceType` 解析未知值时回退 [MANUAL]，
 *       因此新增取值必须同步 `data/mapper/SourceTypeMapping.kt`，否则来源会静默错乱。
 */
enum class SourceType {

    /** 通知监听捕获（支付宝 / 微信 / 银行 App 的支付通知） */
    NOTIFICATION,

    /** 短信解析（银行交易短信） */
    SMS,

    /** 用户手动记账 */
    MANUAL,

    /** L2 无障碍：由页面文本识别形成的账单 */
    ACCESSIBILITY,

    /** L3 截图 OCR：由截图文本识别形成的账单 */
    SCREENSHOT
}
