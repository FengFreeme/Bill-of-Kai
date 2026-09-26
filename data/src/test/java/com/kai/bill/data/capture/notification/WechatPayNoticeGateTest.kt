package com.kai.bill.data.capture.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WechatPayNoticeGateTest {

    private val wechat = "com.tencent.mm"

    @Test
    fun `微信支付通知-放行`() {
        assertTrue(WechatPayNoticeGate.passes(wechat, "微信支付 [2条]微信支付: 退款到账通知"))
    }

    @Test
    fun `微信支付通知-正文带金额也放行`() {
        assertTrue(WechatPayNoticeGate.passes(wechat, "微信支付 微信支付凭证 支付成功 ¥28.30"))
    }

    @Test
    fun `微信聊天通知-不通过`() {
        assertFalse(WechatPayNoticeGate.passes(wechat, "老爸 [视频号]瑞核助残-老陈的视频"))
    }

    @Test
    fun `微信系统提示-不通过`() {
        assertFalse(WechatPayNoticeGate.passes(wechat, "微信 正在运行"))
    }

    @Test
    fun `其它包不走本闸门`() {
        assertTrue(WechatPayNoticeGate.passes("com.eg.android.AlipayGphone", "支付宝 支付成功 ¥88.50"))
    }
}
