package com.kai.bill.data.capture.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationWhitelistTest {

    @Test
    fun `支付宝和微信在白名单`() {
        assertTrue(NotificationWhitelist.isWatched("com.eg.android.AlipayGphone"))
        assertTrue(NotificationWhitelist.isWatched("com.tencent.mm"))
    }

    @Test
    fun `招商银行在白名单`() {
        assertTrue(NotificationWhitelist.isWatched("cmb.pb"))
    }

    @Test
    fun `无关应用不在白名单`() {
        assertFalse(NotificationWhitelist.isWatched("com.android.chrome"))
        assertFalse(NotificationWhitelist.isWatched("com.example.unknown"))
    }
}
