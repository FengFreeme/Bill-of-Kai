package com.kai.bill.data.capture.signal

import com.kai.bill.domain.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 信号文本解析的单测。
 *
 * 这是**建账金额的最后一道关**：抽错一个数字就会凭空多出一笔错账，
 * 而且用户很难发现是解析错了还是自己记错了。因此重点盯住：
 *
 * 1. 「标签邻近」优先于「位置靠前」—— 账单页第一个数字常常是优惠额或商品额；
 * 2. 拿不准时必须返回 null（宁可不建账，也不建错账）；
 * 3. 卡号尾号这类裸数字永远不能被当成金额。
 */
class SignalTextParserTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    /** 固定「现在」为 2026-09-19 16:00，用于验证省略年份时的推断 */
    private val fixedNow: Long = LocalDateTime.of(2026, 9, 19, 16, 0)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    private val clock = object : Clock {
        override fun nowMillis(): Long = fixedNow
    }

    // ---------------- 金额 ----------------

    @Test
    fun `标签紧邻时可抽到`() {
        assertEquals(2830L, SignalTextParser.extractAmountCents("实付 ¥28.30"))
        assertEquals(2830L, SignalTextParser.extractAmountCents("支付金额 28.30元"))
    }

    @Test
    fun `多个金额时取实付而非商品金额`() {
        // 账单详情页典型形态：两个金额只隔几个字，「位置靠前」会选错
        assertEquals(
            2830L,
            SignalTextParser.extractAmountCents("商品金额 ¥25.00 实付金额 ¥28.30")
        )
    }

    @Test
    fun `优惠金额不会被当成实付`() {
        assertEquals(2830L, SignalTextParser.extractAmountCents("优惠 10.00 实付 ¥28.30"))
    }

    @Test
    fun `无标签但全页只有一个金额时才采纳`() {
        assertEquals(12800L, SignalTextParser.extractAmountCents("本月支出 ¥128.00"))
    }

    @Test
    fun `无标签且存在多个金额时放弃`() {
        assertNull(SignalTextParser.extractAmountCents("¥12.00 与 ¥25.00"))
    }

    @Test
    fun `千分位可解析`() {
        assertEquals(123456L, SignalTextParser.extractAmountCents("合计 ¥1,234.56"))
    }

    @Test
    fun `卡号尾号不是金额`() {
        assertNull(
            SignalTextParser.extractAmountCents("您账户2836于09月13日发生快捷支付扣款")
        )
    }

    @Test
    fun `零元不算金额`() {
        assertNull(SignalTextParser.extractAmountCents("本次优惠 0.00元"))
    }

    @Test
    fun `账户资产数字不当交易金额`() {
        // 真机复现（2026-09-19）：支付宝「小荷包」页的「总金额 564.18」被当成了一笔交易。
        // 「总金额」里的「金额」会命中泛化标签，必须在候选阶段就剔除。
        assertNull(
            SignalTextParser.extractAmountCents(
                "总金额(元) 564.18 定期金额¥0.00 累计收益¥0.09 月支出 ¥1191.27 月收入 ¥1755.36"
            )
        )
    }

    @Test
    fun `只有余额时也不采纳`() {
        assertNull(SignalTextParser.extractAmountCents("账户余额 564.18"))
    }

    @Test
    fun `空文本返回null`() {
        assertNull(SignalTextParser.extractAmountCents(""))
        assertNull(SignalTextParser.extractAmountCents("   "))
    }

    // ---------------- 交易时间 ----------------

    @Test
    fun `完整日期可解析`() {
        assertTime("2026-09-19 15:32", 2026, 9, 19, 15, 32)
    }

    @Test
    fun `中文日期可解析`() {
        assertTime("2026年9月19日 15:32", 2026, 9, 19, 15, 32)
    }

    @Test
    fun `省略年份时按今年推断`() {
        assertTime("09-19 15:32", 2026, 9, 19, 15, 32)
    }

    @Test
    fun `省略年份且落在未来时退回上一年`() {
        // 12-31 比「今年 9-19」晚出一个月，只可能是去年的交易
        assertTime("12-31 23:00", 2025, 12, 31, 23, 0)
    }

    @Test
    fun `非法日期返回null`() {
        assertNull(SignalTextParser.extractTradeTimeMillis("2026-02-30 10:00", clock))
    }

    @Test
    fun `文本中没有时间时返回null`() {
        assertNull(SignalTextParser.extractTradeTimeMillis("实付 ¥28.30", clock))
    }

    private fun assertTime(
        text: String,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ) {
        val millis = SignalTextParser.extractTradeTimeMillis(text, clock)
        checkNotNull(millis) { "未能从「$text」解析出交易时间" }
        val zoned = Instant.ofEpochMilli(millis).atZone(zone)
        assertEquals(year.toLong(), zoned.year.toLong())
        assertEquals(month.toLong(), zoned.monthValue.toLong())
        assertEquals(day.toLong(), zoned.dayOfMonth.toLong())
        assertEquals(hour.toLong(), zoned.hour.toLong())
        assertEquals(minute.toLong(), zoned.minute.toLong())
    }
}
