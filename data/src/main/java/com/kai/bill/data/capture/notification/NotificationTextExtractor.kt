package com.kai.bill.data.capture.notification

import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

/**
 * 跨品牌通知文本抽取。
 *
 * 各家 ROM 的短信 / 支付通知样式不一（小米 HyperOS、华为 EMUI、OPPO ColorOS、vivo OriginOS、三星 OneUI），
 * 同一笔交易在不同机型上可能以「普通文本 / 大文本 / 收件箱多行 / 会话样式（MessagingStyle）」呈现。
 * 这里统一用 [NotificationCompat] 兼容 API 读取多种样式，清洗为单行原始文本，供后续规则引擎解析。
 *
 * 设计约束（见文档 §8.1）：
 * - 只在 [android.service.notification.NotificationListenerService.onNotificationPosted] 内轻量执行，
 *   只读 `extras` 与字符串处理，绝不在此做 IO / 数据库写入；
 * - 抽取结果可能含冗余（标题/正文重复），规则引擎层再负责清洗，这里只保证「尽量拿到完整文本」。
 */
object NotificationTextExtractor {

    fun extract(sbn: StatusBarNotification): String? {
        val notification = sbn.notification ?: return null
        val extras = notification.extras ?: return null

        val parts = mutableListOf<CharSequence>()

        // 普通 / 大文本样式（支付宝、微信支付、多数推送）
        extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.let(parts::add)
        extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.let(parts::add)
        extras.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT)?.let(parts::add)
        extras.getCharSequence(NotificationCompat.EXTRA_SUB_TEXT)?.let(parts::add)
        extras.getCharSequence(NotificationCompat.EXTRA_SUMMARY_TEXT)?.let(parts::add)

        // InboxStyle：多行（部分 ROM 的短信 / 邮件通知）
        extras.getCharSequenceArrayList(NotificationCompat.EXTRA_TEXT_LINES)?.let { parts.addAll(it) }

        // MessagingStyle：会话样式（微信、部分短信 App）
        NotificationCompat.MessagingStyle
            .extractMessagingStyleFromNotification(notification)
            ?.messages
            ?.mapNotNull { it.text }
            ?.let { parts.addAll(it) }

        // 兜底：tickerText（旧式通知 / 个别 ROM）
        if (parts.isEmpty()) notification.tickerText?.let { parts.add(it) }

        if (parts.isEmpty()) return null

        return parts
            .asSequence()
            .map { it.toString() }
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.trim() }
            .replace(Regex("\\s+"), " ")
            .trim()
            .takeIf { it.isNotBlank() }
    }
}
