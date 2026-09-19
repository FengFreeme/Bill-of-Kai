package com.kai.bill.data.capture.signal

import com.kai.bill.domain.time.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 类别信号窗口的单测。
 *
 * 盯住三件只在真机上才会暴露的事：
 * 1. **窗口边界** —— 差一毫秒就会「分类补不上」或「把上一笔的信号带进来」；
 * 2. **有效交易时刻也要参与命中** —— L3 截图可能晚于交易数分钟，
 *    只判采集时刻会让信号建账时认不出自己；
 * 3. **有界** —— 容量与留存时长都要真的生效，否则异常应用刷事件会撑大内存。
 */
class CategorySignalStoreTest {

    private var now = 1_700_000_000_000L

    private val clock = object : Clock {
        override fun nowMillis(): Long = now
    }

    private val store = CategorySignalStore(clock)

    private fun signal(
        origin: CategorySignalOrigin = CategorySignalOrigin.ACCESSIBILITY,
        text: String = "美团外卖 ¥28.30",
        capturedAt: Long = now,
        tradeTimeMillis: Long? = null
    ) = CategorySignal(
        origin = origin,
        packageName = "com.sankuai.meituan",
        text = text,
        amountCents = 2830L,
        tradeTimeMillis = tradeTimeMillis,
        capturedAtMillis = capturedAt
    )

    @Test
    fun `未记录时窗口为空`() {
        assertTrue(store.recent(now).isEmpty())
    }

    @Test
    fun `采集时刻在窗口内可命中`() {
        store.record(signal())

        assertEquals(1, store.recent(now).size)
    }

    @Test
    fun `恰好等于窗口半径仍命中-再多一毫秒则不命中`() {
        store.record(signal())

        assertEquals(1, store.recent(now + CategorySignalStore.SIGNAL_TTL_MILLIS).size)
        assertTrue(store.recent(now + CategorySignalStore.SIGNAL_TTL_MILLIS + 1).isEmpty())
    }

    @Test
    fun `有效交易时刻落在窗口内也能命中`() {
        // 场景：用户付款后数分钟才截图 —— 采集时刻离参照时刻很远，只有交易时刻能对上
        val tradeTime = now - 4 * 60_000L
        store.record(signal(capturedAt = now, tradeTimeMillis = tradeTime))

        assertEquals(1, store.recent(tradeTime).size)
    }

    @Test
    fun `高可信来源排在前面-无障碍优先于截图`() {
        store.record(signal(origin = CategorySignalOrigin.SCREENSHOT, text = "截图"))
        store.record(signal(origin = CategorySignalOrigin.ACCESSIBILITY, text = "无障碍"))

        assertEquals("无障碍", store.recent(now).first().text)
    }

    @Test
    fun `同来源按采集时刻倒序`() {
        store.record(signal(text = "旧", capturedAt = now - 1_000L))
        store.record(signal(text = "新", capturedAt = now))

        assertEquals(listOf("新", "旧"), store.recent(now).map { it.text })
    }

    @Test
    fun `超过容量上限时淘汰最旧的信号`() {
        // 全部用同一时刻，避免被时间窗口先筛掉，从而只考察容量淘汰
        repeat(33) { index -> store.record(signal(text = "t$index")) }

        val hit = store.recent(now)
        assertEquals(32, hit.size)
        assertTrue(hit.none { it.text == "t0" })
        assertTrue(hit.any { it.text == "t32" })
    }

    @Test
    fun `超过留存时长后再次记录会清理旧信号`() {
        val oldReference = now
        store.record(signal(text = "旧", capturedAt = oldReference))

        now += 5 * 60_000L + 1
        store.record(signal(text = "新", capturedAt = now))

        // 若旧信号未被清理，按它自己的采集时刻查询本应命中；被清理后必须为空
        assertTrue(store.recent(oldReference).isEmpty())
    }
}
