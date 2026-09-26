package com.kai.bill.data.capture.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDiagnosticTextTest {

    @Test
    fun `各渠道给出对应标签`() {
        assertEquals("支付宝专属", NotificationDiagnosticText.channelLabel("com.eg.android.AlipayGphone"))
        assertEquals("微信专属", NotificationDiagnosticText.channelLabel("com.tencent.mm"))
        assertEquals("银行专属", NotificationDiagnosticText.channelLabel("cmb.pb"))
        assertEquals("短信专属", NotificationDiagnosticText.channelLabel("com.android.mms"))
        assertEquals("通用", NotificationDiagnosticText.channelLabel("com.sankuai.meituan"))
    }

    @Test
    fun `前缀格式与识别记录一致-展示侧可直接复用`() {
        val text = NotificationDiagnosticText.withChannelSummary(
            packageName = "com.tencent.mm",
            rawText = "微信支付 已支付¥14.70"
        )

        assertEquals("识别方式 微信专属 ｜ 来源 通知 ｜ 微信支付 已支付¥14.70", text)
    }

    @Test
    fun `前缀在最前-截断后仍看得到渠道`() {
        val text = NotificationDiagnosticText.withChannelSummary(
            packageName = "com.android.mms",
            rawText = "X".repeat(500)
        )

        assertTrue(text.startsWith("识别方式 短信专属 ｜ 来源 通知 ｜ "))
    }
}
