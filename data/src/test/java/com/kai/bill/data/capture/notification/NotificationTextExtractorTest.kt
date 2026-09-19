package com.kai.bill.data.capture.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 通知文本归一化。
 *
 * 拼接冗余会一路写进采集诊断与账单 `rawText`（真机上表现为「同一句话出现两遍」），
 * 而 `extract` 的入参是 Android 的 `StatusBarNotification`、JVM 单测造不出来，
 * 因此把可测的那一段（归一化 + 拼接）单独钉在这里。
 */
class NotificationTextExtractorTest {

    @Test
    fun `正文与大文本完全相同-只保留一段`() {
        // 真机样本：小荷包资金变动通知的 EXTRA_TEXT 与 EXTRA_BIG_TEXT 内容一致
        val joined = NotificationTextExtractor.normalizeAndJoin(
            listOf("小荷包「烟火储备金」资金变动", "凯支付了12.39元，点击查看详情>", "凯支付了12.39元，点击查看详情>")
        )

        assertEquals("小荷包「烟火储备金」资金变动 凯支付了12.39元，点击查看详情>", joined)
    }

    @Test
    fun `大文本包含正文-保留信息量更大的那条`() {
        // 大文本是正文的完整版时，两者是包含关系而非相等，短的那条要丢掉
        val joined = NotificationTextExtractor.normalizeAndJoin(
            listOf("交易提醒", "你有一笔6.12元的支出", "交易提醒 你有一笔6.12元的支出，点击领取2个积分。")
        )

        assertEquals("交易提醒 你有一笔6.12元的支出，点击领取2个积分。", joined)
    }

    @Test
    fun `互不包含的片段-全部保留且按出现顺序`() {
        val joined = NotificationTextExtractor.normalizeAndJoin(
            listOf("支付宝", "收款到账 ¥0.50")
        )

        assertEquals("支付宝 收款到账 ¥0.50", joined)
    }

    @Test
    fun `等长的不同片段不会被误删`() {
        // 等长意味着不可能互相包含，两条都必须留下
        val joined = NotificationTextExtractor.normalizeAndJoin(
            listOf("微信红包", "收到转账")
        )

        assertEquals("微信红包 收到转账", joined)
    }

    @Test
    fun `空白片段被忽略`() {
        assertNull(NotificationTextExtractor.normalizeAndJoin(listOf("", "   ")))
    }

    @Test
    fun `多行片段被压成单行`() {
        val joined = NotificationTextExtractor.normalizeAndJoin(
            listOf("第一行\n第二行", "另一句")
        )

        assertEquals("第一行 第二行 另一句", joined)
    }
}
