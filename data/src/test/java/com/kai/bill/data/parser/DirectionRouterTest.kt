package com.kai.bill.data.parser

import com.kai.bill.data.presets.DefaultMatchKeywords
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.PendingReason
import com.kai.bill.domain.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2 方向路由的单测。
 *
 * 判定顺序是错账的唯一防线，因此每个「顺序」都有一条对应用例：
 * 排除词优先 → 还款类判转账 → 具体收入词压泛化入账 → 泛化入账 / 自己的钱搬家 / 红包 → 待确认。
 */
class DirectionRouterTest {

    private val router = DirectionRouter()
    private val tables = DefaultMatchKeywords.tables

    private fun route(text: String): DirectionHit =
        router.route(text, SourceType.NOTIFICATION, tables)

    // —— 直接落库：支出 / 收入 ——

    @Test
    fun `支付成功-判支出并计入统计`() {
        val hit = route("微信支付 支付成功 ¥0.01")
        assertEquals(MatchRoute.EXPENSE, hit.route)
        assertEquals(BillType.EXPENSE, hit.type)
        assertTrue(hit.countInStats)
        assertFalse(hit.isPending)
    }

    @Test
    fun `收款到账-判收入并计入统计`() {
        val hit = route("收款到账 ￥0.50 已存入余额")
        assertEquals(MatchRoute.INCOME, hit.route)
        assertTrue(hit.countInStats)
    }

    @Test
    fun `支付宝里的支付不是动作词`() {
        // 「支付」是「支付宝」的子串：靠保留词占位屏蔽，而不是把「支付」从词表里删掉。
        // 否则每一条支付宝通知都会被判成支出（收款记成支出、转账记成支出）。
        val hit = route("支付宝 收款 ¥12.00")
        assertEquals(MatchRoute.INCOME, hit.route)
        assertEquals("收款", hit.matchedKeyword)
    }

    @Test
    fun `保留词之外的裸支付照常识别`() {
        // 屏蔽只作用于保留词区间**内**的出现位置，不影响同一句话里真正的动作词
        val hit = route("支付宝 支付 ¥88.00")
        assertEquals(MatchRoute.EXPENSE, hit.route)
        assertEquals("支付", hit.matchedKeyword)
    }

    @Test
    fun `有商户无动作词-由分类词兜底方向`() {
        // 「支付宝 星巴克 ¥35」没有支付 / 消费这类动作词，靠分类词的方向定方向，
        // 否则这类最常见的小额支付会全部掉进「判不出方向」
        val hit = route("支付宝 星巴克 ¥35.00")
        assertEquals(MatchRoute.EXPENSE, hit.route)
        assertEquals("星巴克", hit.matchedKeyword)
    }

    @Test
    fun `具体收入词压过泛化入账`() {
        // 「工资」优先于「入账」，否则这笔会被降级成待确认（甚至虚增收入）
        assertEquals(MatchRoute.INCOME, route("工资代发 入账 ¥8000.00").route)
    }

    // —— 直接落库：转账（不计统计）——

    @Test
    fun `信用卡还款-判转账且不计统计`() {
        val hit = route("信用卡还款 ¥5,000.00")
        assertEquals(MatchRoute.TRANSFER, hit.route)
        assertEquals(BillType.TRANSFER, hit.type)
        assertFalse(hit.countInStats)
        assertFalse(hit.isPending)
    }

    // —— 待确认 ——

    @Test
    fun `泛化入账-降级为待确认`() {
        // 无法区分「别人真给钱」与「自己账户互转」，宁可不记也不虚增收入
        val hit = route("您尾号5678账户09月13日入账人民币100.00元")
        assertTrue(hit.isPending)
        assertEquals(PendingReason.GENERIC_INBOUND, hit.pendingReason)
        assertNull(hit.type)
        assertFalse(hit.countInStats)
    }

    @Test
    fun `转出成功-自己的钱搬家-待确认`() {
        val hit = route("转出成功 ¥0.80 到账账户 支付宝余额")
        assertEquals(MatchRoute.SELF_TRANSFER, hit.route)
        assertEquals(PendingReason.SELF_TRANSFER, hit.pendingReason)
        assertTrue(hit.isPending)
    }

    @Test
    fun `微信红包-待确认`() {
        assertEquals(PendingReason.GIFT, route("微信红包 你领取了XX的红包 ¥5.20").pendingReason)
    }

    @Test
    fun `无任何线索-判不出方向`() {
        val hit = route("您尾号1234账户人民币88.50元")
        assertNull(hit.route)
        assertEquals(PendingReason.DIRECTION_UNKNOWN, hit.pendingReason)
        assertTrue(hit.isPending)
    }

    // —— 排除词优先于一切 ——

    @Test
    fun `支付失败-被排除`() {
        // 不先拦住就会记成一笔真实支出
        val hit = route("支付宝 支付失败 ¥88.00")
        assertTrue(hit.isExcluded)
        assertFalse(hit.isPending)
        assertNull(hit.type)
    }

    @Test
    fun `账单预告-被排除`() {
        assertTrue(route("信用卡账单已出，本期应还 ¥1,200.00").isExcluded)
        assertTrue(route("将于9月20日自动扣款 ¥88.00").isExcluded)
    }

    @Test
    fun `营销文案-被排除`() {
        assertTrue(route("满50元减10元，实付 ¥88").isExcluded)
    }
}
