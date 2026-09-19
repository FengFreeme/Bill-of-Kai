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
 * - 抽取结果按「去重 + 丢掉被包含的短片段」归一化后再拼接：厂商常把同一句话塞进多个 extras
 *   （正文 [NotificationCompat.EXTRA_TEXT] 与大文本 [NotificationCompat.EXTRA_BIG_TEXT] 尤其常见），
 *   原样拼接会让同一段话在采集诊断与账单 `rawText` 里出现两遍。
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

        return normalizeAndJoin(parts)
    }

    /**
     * 片段归一化 + 拼接。
     *
     * 做两件事：
     * 1. **去重**：同一句话被塞进 [NotificationCompat.EXTRA_TEXT] 与
     *    [NotificationCompat.EXTRA_BIG_TEXT] 时内容完全一致，原样拼接会出现两遍；
     * 2. **丢掉被包含的短片段**：大文本往往是正文的完整版（包含而非相等），
     *    这时保留信息量更大的那条。
     *
     * `EXTRA_TEXT` 与 `EXTRA_BIG_TEXT` 都取仍是必须的（折叠态下个别厂商只有其一有内容），
     * 这里只消除它们内容重叠带来的冗余。
     *
     * 抽成独立纯函数是为了能单测 —— [extract] 的入参是 Android 的 `StatusBarNotification`，
     * JVM 单测里造不出来。
     */
    internal fun normalizeAndJoin(parts: List<CharSequence>): String? {
        val normalized = parts
            .asSequence()
            .map { it.toString().trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()

        if (normalized.isEmpty()) return null

        // 只丢「被更长的片段完整包含」的短片段；等长且互不相同者不可能互相包含，不会误删
        val deduped = normalized.filter { candidate ->
            normalized.none { other -> other.length > candidate.length && other.contains(candidate) }
        }

        return deduped
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .takeIf { it.isNotBlank() }
    }
}
