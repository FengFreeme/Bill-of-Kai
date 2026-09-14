package com.kai.bill.data.capture.notification

/**
 * 通知监听采集到的原始事件。
 *
 * 监听器只做「轻量抽取 + 白名单过滤」，把结果封装成该事件投喂给桥接 [NotificationCaptureBridge]，
 * 由 [com.kai.bill.data.capture.NotificationCapture] 收集后转交 [com.kai.bill.data.ingest.BillIngestor]。
 *
 * @property packageName 来源 App 包名（支付宝 / 微信 / 短信 App 等）
 * @property rawText 跨品牌抽取并清洗后的单行原始文本（已脱敏，不写日志）
 * @property postedAtMillis 通知投递时间（[android.service.notification.StatusBarNotification.postTime]）
 * @property sbnKey 系统通知唯一键，用于去重与排查
 */
data class RawCaptureEvent(
    val packageName: String,
    val rawText: String,
    val postedAtMillis: Long,
    val sbnKey: String
)
