package com.kai.bill.data.capture.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * sbnKey 重放窗口：判断写反一次就会「重复记账」或「漏记更新件」，
 * 且都只会在真机上被用户发现，因此把窗口边界、只读语义、淘汰上限钉在单测里。
 */
class RecentNotificationKeysTest {

    private val keys = RecentNotificationKeys()

    @Test
    fun `未登记过的通知不算重放`() {
        assertFalse(keys.isDuplicate("k1", 1_000L))
    }

    @Test
    fun `只读判断不会自行登记`() {
        // isDuplicate 必须无副作用：否则一次「抽不到金额」的投递也会上锁，
        // 让随后能记账的更新件被跳过
        keys.isDuplicate("k1", 1_000L)
        assertFalse(keys.isDuplicate("k1", 1_001L))
    }

    @Test
    fun `登记后窗口内的重复投递被判重放`() {
        keys.remember("k1", 1_000L)
        assertTrue(keys.isDuplicate("k1", 1_000L))
        assertTrue(keys.isDuplicate("k1", 1_000L + RecentNotificationKeys.TTL_MILLIS - 1))
    }

    @Test
    fun `到达窗口边界即不再算重放`() {
        keys.remember("k1", 1_000L)
        assertFalse(keys.isDuplicate("k1", 1_000L + RecentNotificationKeys.TTL_MILLIS))
    }

    @Test
    fun `不同 key 互不影响`() {
        keys.remember("k1", 1_000L)
        assertFalse(keys.isDuplicate("k2", 1_000L))
    }

    @Test
    fun `空白 key 不作为去重依据`() {
        // 个别 ROM 可能给不出 key，此时必须退回「不拦」，宁可重复也不能因空串把无关通知全判成重放
        keys.remember("", 1_000L)
        assertFalse(keys.isDuplicate("", 1_000L))
    }

    @Test
    fun `重新登记会刷新窗口起点`() {
        keys.remember("k1", 1_000L)
        keys.remember("k1", 9_000L)
        // 距首次登记已 14 秒（超过窗口），但距最近一次登记仅 6 秒 —— 仍算重放，
        // 以此锁定「窗口滑动、持续更新件一直被挡」的语义
        assertTrue(keys.isDuplicate("k1", 15_000L))
    }

    @Test
    fun `超过容量上限时淘汰最旧的 key`() {
        // 全部用同一时间戳，避免被时间窗口先清掉，从而只考察容量淘汰
        repeat(65) { index -> keys.remember("k$index", 1_000L) }

        assertFalse(keys.isDuplicate("k0", 1_000L))  // 最旧的一条被淘汰
        assertTrue(keys.isDuplicate("k64", 1_000L))
    }
}
