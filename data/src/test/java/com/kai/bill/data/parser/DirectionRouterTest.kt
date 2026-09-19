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

    // —— 个人转账：转入 / 转出（两张真机页面样本）——
    //
    // 两个方向的页面文案都带「收款」，方向判错会把「转出」记成一笔收入（虚增收入）。
    // 下面两条钉住「都要判成转账」，后两条钉住「不许误伤付款页」。

    @Test
    fun `转账转出页-他人已收款-判转账而非收入`() {
        // 付款方视角的转账结果页：「周建鑫已收款」= 我把钱转给了周建鑫。
        // 全页只有「已收款」这三个字可判，按裸的「收款」判会被记成一笔 ¥200 的收入。
        val hit = route("周建鑫已收款 ¥200.00 转账时间 2026年09月10日 22:22:08 收款时间 2026年09月10日 22:22:44 账单详情")
        assertEquals(MatchRoute.TRANSFER, hit.route)
        assertEquals(BillType.TRANSFER, hit.type)
        assertFalse(hit.countInStats)
        assertFalse(hit.isPending)
    }

    @Test
    fun `转账转入页-你已收款-判转账而非收入`() {
        val hit = route(
            "你已收款，资金已存入零钱 ¥100.00 零钱余额 " +
                "转账时间 2026年09月10日 17:24:47 收款时间 2026年09月10日 17:24:55 账单详情"
        )
        assertEquals(MatchRoute.TRANSFER, hit.route)
        assertFalse(hit.countInStats)
        // 不钉具体命中词：转账组里有「你已收款」「资金已存入零钱」两条强词，
        // 同优先级时**更长的胜出**，因此这页命中的是后者 —— 两者结论相同，都不该被写死。
        assertTrue(hit.matchedKeyword in setOf("你已收款", "资金已存入零钱"))
    }

    @Test
    fun `付款成功页的对方已收款-仍判支出`() {
        // 「对方已收款」也出现在**付款成功**页里，所以它不能进方向词表 ——
        // 一旦收进来，每一笔付款都会被判成转账。这条用例守住这个边界。
        val hit = route("微信支付 使用零钱支付 ¥1.00 交易状态 支付成功 收款方 Sparking 对方已收款")
        assertEquals(MatchRoute.EXPENSE, hit.route)
    }

    @Test
    fun `扫码付款的账单详情-仍判支出`() {
        // 微信「扫码付款」的账单详情里同样有「转账时间 / 转账单号」，
        // 因此这两个字段**不能**当作「这是转账」的证据，否则真支出会被记成转账。
        val hit = route(
            "全部账单 本服务由财付通提供 当前状态 支付成功 收款方备注 二维码收款 支付方式 零钱 " +
                "转账时间 2026年9月18日 23:09:06 转账单号 10001073012026091800609962660139 " +
                "账单服务 对订单有疑惑 扫二维码付款-给Sparking -0.10"
        )
        assertEquals(MatchRoute.EXPENSE, hit.route)
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
